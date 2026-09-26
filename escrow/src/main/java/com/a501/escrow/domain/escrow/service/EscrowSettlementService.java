package com.a501.escrow.domain.escrow.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.escrow.dto.EscrowSettlementDtos;
import com.a501.escrow.domain.escrow.repository.EscrowRepository;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.escrow.repository.LockedEscrow;
import com.a501.escrow.domain.escrow.repository.SettlementTransaction;
import com.a501.escrow.integration.finance.FinanceApiException;
import com.a501.escrow.integration.finance.FinanceClient;
import com.a501.escrow.integration.finance.FinanceClientUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EscrowSettlementService {
    private static final BigInteger TEN_THOUSAND = BigInteger.valueOf(10_000);
    private static final BigInteger FEE_RATE_BPS = BigInteger.valueOf(100); // R-PAY-07: 1%
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String RELEASE = "RELEASE";
    private static final String HOLDER_MISMATCH = "E5016";
    private static final DateTimeFormatter REFERENCE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InternalApiSecurity security;
    private final EscrowRepository escrows;
    private final EscrowSettlementRepository settlements;
    private final RequestFingerprint fingerprints;
    private final FieldEncryption encryption;
    private final FinanceClient finance;
    private final FinanceProperties properties;
    private final Clock clock;
    private final ZoneId financeZone;
    private final String instanceId;
    private final TransactionTemplate transaction;

    public EscrowSettlementService(InternalApiSecurity security, EscrowRepository escrows,
            EscrowSettlementRepository settlements, RequestFingerprint fingerprints,
            FieldEncryption encryption, FinanceClient finance, FinanceProperties properties,
            Clock clock, PlatformTransactionManager transactionManager,
            @Value("${POD_NAME:local-writer}") String instanceId) {
        this.security = security;
        this.escrows = escrows;
        this.settlements = settlements;
        this.fingerprints = fingerprints;
        this.encryption = encryption;
        this.finance = finance;
        this.properties = properties;
        this.clock = clock;
        this.financeZone = ZoneId.of(properties.timeZone());
        this.instanceId = instanceId;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public EscrowSettlementDtos.ReleaseRecord release(EscrowSettlementDtos.ReleaseRequest request) {
        AuthenticatedCaller.Client caller = client(request.header(), "releaseEscrow");
        String fingerprint = fingerprints.create(request);
        SettlementTransaction payout = settlements.findByKey(caller.serviceClientId(), RELEASE,
                request.header().idempotencyKey()).orElse(null);
        if (payout == null) {
            payout = transaction.execute(status -> createRelease(caller.serviceClientId(), request, fingerprint));
        }
        requireRequest(payout, request.escrowId(), request.referenceId(), fingerprint, request.header());
        try {
            execute(payout, "PAYOUT_TRANSFER", request.header());
        } finally {
            abandonFeeAfterFailedPayout(payout, request.referenceId());
        }
        SettlementTransaction fee = settlements.findByReference(request.escrowId(), "FEE", request.referenceId()).orElse(null);
        if (COMPLETED.equals(settlements.findById(payout.id()).orElseThrow().status()) && fee != null) {
            execute(fee, "FEE_TRANSFER", request.header());
        }
        settlements.refreshSettlement(payout.settlementId(), payout.escrowId());
        return releaseResult(settlements.findById(payout.id()).orElseThrow());
    }

    /**
     * 수수료는 지급이 완료된 뒤에만 보낸다. 지급이 FAILED로 끝났으면 수수료도 보내지 않은 채 FAILED로 닫아
     * 정산이 FAILED로 확정되게 한다. 자금 유출은 없다. (R-PAY-12)
     */
    private void abandonFeeAfterFailedPayout(SettlementTransaction payout, String reference) {
        if (!FAILED.equals(settlements.findById(payout.id()).orElseThrow().status())) return;
        settlements.findByReference(payout.escrowId(), "FEE", reference)
                .filter(fee -> "PENDING".equals(fee.status()))
                .ifPresent(fee -> {
                    settlements.failed(fee.id(), "PAYOUT_FAILED");
                    settlements.refreshSettlement(payout.settlementId(), payout.escrowId());
                });
    }

    private SettlementTransaction createRelease(long clientId, EscrowSettlementDtos.ReleaseRequest request,
            String fingerprint) {
        LockedEscrow escrow = lockActive(clientId, request.escrowId(), request.header());
        SettlementTransaction concurrent = settlements.findByKey(clientId, RELEASE,
                request.header().idempotencyKey()).orElse(null);
        if (concurrent != null) return concurrent;
        requireNotHeld(escrow, request.header());
        if (settlements.settlementId(escrow.id(), request.referenceId()).isPresent()) {
            throw error("E5010", "기존 정산이 있습니다.", request.header());
        }
        BigInteger balance = settlements.balance(escrow.id());
        if (request.amount().compareTo(balance) > 0) {
            throw error("E5008", "에스크로 잔액이 부족합니다.", request.header());
        }
        BigInteger fee = request.amount().multiply(FEE_RATE_BPS).divide(TEN_THOUSAND);
        BigInteger net = request.amount().subtract(fee);
        return settlements.createRelease(clientId, escrow.id(), request.referenceId(), request.amount(), net, fee,
                request.header().idempotencyKey(), fingerprint, request.recipientBankCode(),
                encryption.encrypt(request.recipientAccountNo()), request.recipientName(),
                encryption.encrypt(properties.feeAccountNo()));
    }

    public EscrowSettlementDtos.RefundRecord refund(EscrowSettlementDtos.RefundRequest request) {
        AuthenticatedCaller.Client caller = client(request.header(), "refundEscrow");
        String fingerprint = fingerprints.create(request);
        SettlementTransaction refund = settlements.findByKey(caller.serviceClientId(), "REFUND",
                request.header().idempotencyKey()).orElse(null);
        if (refund == null) {
            refund = transaction.execute(status -> createRefund(caller.serviceClientId(), request, fingerprint));
        }
        requireRequest(refund, request.escrowId(), request.referenceId(), fingerprint, request.header());
        execute(refund, "REFUND_TRANSFER", request.header());
        settlements.refreshSettlement(refund.settlementId(), refund.escrowId());
        SettlementTransaction current = settlements.findById(refund.id()).orElseThrow();
        BigInteger amount = COMPLETED.equals(current.status()) ? current.amount() : BigInteger.ZERO;
        return new EscrowSettlementDtos.RefundRecord(current.id(), amount,
                settlements.balance(current.escrowId()), current.status());
    }

    private SettlementTransaction createRefund(long clientId, EscrowSettlementDtos.RefundRequest request,
            String fingerprint) {
        LockedEscrow escrow = lockActive(clientId, request.escrowId(), request.header());
        SettlementTransaction concurrent = settlements.findByKey(clientId, "REFUND",
                request.header().idempotencyKey()).orElse(null);
        if (concurrent != null) return concurrent;
        requireNotHeld(escrow, request.header());
        var source = settlements.fundingSource(escrow.id()).orElseThrow(() ->
                error("E5007", "완료된 예치 거래가 없습니다.", request.header()));
        if (!request.refundBankCode().equals(source.bankCode())
                || !request.refundAccountNo().equals(encryption.decrypt(source.accountEncrypted()))) {
            throw error("E5009", "환불 계좌가 예치 출금 계좌와 다릅니다.", request.header());
        }
        BigInteger balance = settlements.balance(escrow.id());
        if (!request.amount().equals(balance)) { // R-PAY-04: 남은 금액 전액만 환불
            throw error("E5013", "남은 에스크로 금액 전액만 환불할 수 있습니다.", request.header());
        }
        Long settlementId = settlements.settlementId(escrow.id(), request.referenceId()).orElse(null);
        if (settlementId == null) {
            return settlements.createRefundSettlement(clientId, escrow.id(), request.referenceId(), request.amount(),
                    request.header().idempotencyKey(), fingerprint, request.refundBankCode(),
                    encryption.encrypt(request.refundAccountNo()), request.refundHolderName());
        }
        String status = settlements.settlementStatus(settlementId);
        if ("PROCESSING".equals(status) || UNKNOWN.equals(status) || FAILED.equals(status)) {
            throw error("E5010", "진행 중이거나 종료된 정산에는 환불을 추가할 수 없습니다.", request.header());
        }
        return settlements.createRefund(clientId, escrow.id(), settlementId, request.referenceId(), request.amount(),
                request.header().idempotencyKey(), fingerprint, request.refundBankCode(),
                encryption.encrypt(request.refundAccountNo()), request.refundHolderName());
    }

    private void execute(SettlementTransaction transactionRow, String transferStep, EscrowRequestHeader header) {
        SettlementTransaction current = settlements.findById(transactionRow.id()).orElseThrow();
        // R-PAY-12: 완료와 실패는 확정된 결과다. 같은 정산 식별자로 다시 불러도 이체하지 않고 현재 결과를 돌려준다.
        if (COMPLETED.equals(current.status()) || FAILED.equals(current.status())) return;
        if (UNKNOWN.equals(current.status())) {
            recover(current);
            current = settlements.findById(current.id()).orElseThrow();
            if (!isOpen(current)) return;
        }
        try {
            if (!verifyHolder(current, header)) return;
            current = settlements.findById(current.id()).orElseThrow();
            if (!isOpen(current)) return;
            settlements.processing(current.id());
            Attempt attempt = claimTransfer(current.id(), transferStep);
            if (attempt == null) return;
            transfer(current, attempt);
        } catch (FinanceApiException exception) {
            settlements.failed(current.id(), exception.code());
            settlements.refreshSettlement(current.settlementId(), current.escrowId());
            throw new EscrowApiException(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), header);
        } catch (FinanceClientUnavailableException exception) {
            settlements.unknown(current.id());
            settlements.refreshSettlement(current.settlementId(), current.escrowId());
        }
    }

    private boolean verifyHolder(SettlementTransaction row, EscrowRequestHeader header) {
        Attempt attempt = claim(row.id(), "HOLDER_VERIFY");
        if (attempt == null) return false;
        String account = encryption.decrypt(row.recipientAccountEncrypted());
        FinanceClient.AccountHolder holder;
        try {
            holder = finance.inquireAccountHolder(properties.escrowUserKey(), account);
        } catch (FinanceApiException exception) {
            settlements.finishAttempt(attempt.id(), FAILED, null, exception.code());
            throw exception;
        } catch (FinanceClientUnavailableException exception) {
            settlements.finishAttempt(attempt.id(), UNKNOWN, null, null);
            throw exception;
        }
        boolean accountMismatch = !row.recipientBankCode().equals(holder.bankCode())
                || !account.equals(holder.accountNo());
        boolean nameMismatch = !"FEE".equals(row.type()) && !row.recipientName().equals(holder.userName());
        if (accountMismatch || nameMismatch) {
            settlements.finishAttempt(attempt.id(), FAILED, null, HOLDER_MISMATCH);
            settlements.failed(row.id(), HOLDER_MISMATCH);
            settlements.refreshSettlement(row.settlementId(), row.escrowId());
            throw error(HOLDER_MISMATCH, "금융망 예금주명과 일치하지 않습니다.", header);
        }
        if ("FEE".equals(row.type())) settlements.recipientName(row.id(), holder.userName());
        settlements.finishAttempt(attempt.id(), "SUCCEEDED", account, "H0000");
        return true;
    }

    private void transfer(SettlementTransaction row, Attempt attempt) {
        try {
            String account = encryption.decrypt(row.recipientAccountEncrypted());
            String externalId = finance.transfer(properties.escrowUserKey(), account,
                    properties.escrowAccountNo(), row.amount(), row.recipientName(),
                    summary(row), attempt.reference());
            settlements.transferSucceeded(row.id(), attempt.id(), externalId);
        } catch (FinanceApiException exception) {
            settlements.transferFailed(row.id(), attempt.id(), exception.code());
            throw exception;
        } catch (FinanceClientUnavailableException exception) {
            settlements.finishAttempt(attempt.id(), UNKNOWN, null, null);
            throw exception;
        }
    }

    private void recover(SettlementTransaction row) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(financeZone));
            finance.inquireTransactions(properties.escrowUserKey(), properties.escrowAccountNo(),
                    today.minusDays(7), today, "A", "DESC").stream()
                    .filter(item -> summary(row).equals(item.transactionSummary()))
                    .filter(item -> row.amount().equals(new BigInteger(item.transactionBalance())))
                    .findFirst().ifPresent(item -> settlements.complete(row.id(), item.transactionUniqueNo()));
        } catch (RuntimeException ignored) {
            // R-PAY-12: 조회로 확정하지 못한 결과는 UNKNOWN을 유지한다.
        }
    }

    private EscrowSettlementDtos.ReleaseRecord releaseResult(SettlementTransaction payout) {
        BigInteger net = settlements.completedAmount(payout.settlementId(), RELEASE);
        BigInteger fee = settlements.completedAmount(payout.settlementId(), "FEE");
        String settlementStatus = settlements.settlementStatus(payout.settlementId());
        String apiStatus = switch (settlementStatus) {
            case "CLOSED", "OPEN" -> COMPLETED;
            default -> settlementStatus;
        };
        return new EscrowSettlementDtos.ReleaseRecord(payout.id(), net.add(fee), fee, net,
                settlements.balance(payout.escrowId()), apiStatus);
    }

    private LockedEscrow lockActive(long clientId, long escrowId, EscrowRequestHeader header) {
        LockedEscrow escrow = escrows.findOwnedByIdForUpdate(clientId, escrowId)
                .orElseThrow(() -> error("E5004", "에스크로를 찾을 수 없습니다.", header));
        if (!"ACTIVE".equals(escrow.status())) throw error("E5007", "예치 완료 상태가 아닙니다.", header);
        return escrow;
    }

    private void requireNotHeld(LockedEscrow escrow, EscrowRequestHeader header) {
        if ("HELD".equals(escrow.holdStatus())) throw error("E5005", "동결 중인 에스크로입니다.", header);
    }

    private void requireRequest(SettlementTransaction row, long escrowId, String reference,
            String fingerprint, EscrowRequestHeader header) {
        if (row.escrowId() != escrowId || !row.referenceId().equals(reference)
                || !row.requestFingerprint().equals(fingerprint)) {
            throw error("E5003", "같은 멱등키로 다른 요청이 접수되었습니다.", header);
        }
    }

    private AuthenticatedCaller.Client client(EscrowRequestHeader header, String apiName) {
        return (AuthenticatedCaller.Client) security.authenticate(header, apiName, ApiRequirements.write(false));
    }

    private Attempt claim(long transactionId, String step) {
        return dispatch(settlements.prepareAttempt(transactionId, step, nextReference()));
    }

    /** 송금 시도는 거래가 아직 끝나지 않았을 때만 만든다. 다른 Pod가 먼저 끝냈으면 비어 있다. */
    private Attempt claimTransfer(long transactionId, String step) {
        return dispatch(settlements.prepareTransferAttempt(transactionId, step, nextReference()));
    }

    private Attempt dispatch(Optional<Long> prepared) {
        if (prepared.isEmpty()) return null;
        long id = prepared.orElseThrow();
        return settlements.claimAttempt(id, instanceId)
                ? new Attempt(id, settlements.attemptReference(id)) : null;
    }

    private static boolean isOpen(SettlementTransaction row) {
        return "PENDING".equals(row.status()) || "PROCESSING".equals(row.status());
    }

    private String nextReference() {
        return REFERENCE_TIME.format(clock.instant().atZone(financeZone))
                + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String summary(SettlementTransaction row) {
        return "KOMIT-" + row.type() + "-" + row.id();
    }

    private EscrowApiException error(String code, String message, EscrowRequestHeader header) {
        return new EscrowApiException(HttpStatus.BAD_REQUEST, code, message, header);
    }

    private record Attempt(long id, String reference) {
    }
}
