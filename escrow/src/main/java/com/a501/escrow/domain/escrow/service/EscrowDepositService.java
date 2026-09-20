package com.a501.escrow.domain.escrow.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.RequestFingerprint;
import com.a501.escrow.common.crypto.SensitiveDataHasher;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.config.FinanceProperties;
import com.a501.escrow.domain.escrow.dto.EscrowDtos;
import com.a501.escrow.domain.escrow.repository.DepositEscrow;
import com.a501.escrow.domain.escrow.repository.EscrowDepositRepository;
import com.a501.escrow.domain.escrow.repository.EscrowSettlementRepository;
import com.a501.escrow.domain.member.repository.MemberAccountProvision;
import com.a501.escrow.domain.member.repository.MemberAccountRepository;
import com.a501.escrow.integration.finance.FinanceApiException;
import com.a501.escrow.integration.finance.FinanceClient;
import com.a501.escrow.integration.finance.FinanceClientUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscrowDepositService {
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final String COMPLETED = "COMPLETED";
    private static final String SETTLED = "SETTLED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String AMOUNT_MISMATCH = "E5006";
    private static final DateTimeFormatter REFERENCE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InternalApiSecurity security;
    private final EscrowDepositRepository repository;
    private final EscrowSettlementRepository settlements;
    private final MemberAccountRepository members;
    private final RequestFingerprint fingerprints;
    private final SensitiveDataHasher hasher;
    private final FieldEncryption encryption;
    private final FinanceClient finance;
    private final FinanceProperties properties;
    private final Clock clock;
    private final String instanceId;
    private final ZoneId financeZone;

    public EscrowDepositService(InternalApiSecurity security, EscrowDepositRepository repository,
            EscrowSettlementRepository settlements,
            MemberAccountRepository members, RequestFingerprint fingerprints, SensitiveDataHasher hasher,
            FieldEncryption encryption, FinanceClient finance, FinanceProperties properties, Clock clock,
            @Value("${POD_NAME:local-writer}") String instanceId) {
        this.security = security;
        this.repository = repository;
        this.settlements = settlements;
        this.members = members;
        this.fingerprints = fingerprints;
        this.hasher = hasher;
        this.encryption = encryption;
        this.finance = finance;
        this.properties = properties;
        this.clock = clock;
        this.instanceId = instanceId;
        this.financeZone = ZoneId.of(properties.timeZone());
    }

    public EscrowDtos.AmountRecord create(EscrowDtos.CreateRequest request) {
        AuthenticatedCaller.Client caller = client(request.header(), "createEscrow", ApiRequirements.write(true));
        String fingerprint = fingerprints.create(request);
        String key = request.header().idempotencyKey();
        DepositEscrow escrow = repository.findByIdempotencyKey(caller.serviceClientId(), key)
                .orElseGet(() -> createOrRead(caller.serviceClientId(), request, fingerprint));
        requireFingerprint(escrow, fingerprint, request.header());
        execute(escrow, request.header().userKey(), request.header());
        DepositEscrow current = repository.findById(caller.serviceClientId(), escrow.escrowId()).orElseThrow();
        if (UNKNOWN.equals(current.transactionStatus())) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Q1000",
                    "예치 결과를 확인 중입니다. 같은 멱등키로 다시 확인해 주세요.", request.header());
        }
        return amount(current);
    }

    private DepositEscrow createOrRead(long clientId, EscrowDtos.CreateRequest request, String fingerprint) {
        DepositEscrow existing = repository.findByContractId(clientId, request.contractId()).orElse(null);
        if (existing != null) {
            requireFingerprint(existing, fingerprint, request.header());
            return existing;
        }
        String userKeyHash = hasher.userKeyLookupHash(clientId, request.header().userKey());
        MemberAccountProvision member = members.findCompletedByUserKeyHash(clientId, userKeyHash)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "E5017",
                        "회원가입 발급 계좌와 일치하지 않습니다.", request.header()));
        if (!request.withdrawalBankCode().equals(member.bankCode())
                || !request.withdrawalAccountNo().equals(encryption.decrypt(member.accountNoEncrypted()))) {
            throw error(HttpStatus.BAD_REQUEST, "E5017", "회원가입 발급 계좌와 일치하지 않습니다.", request.header());
        }
        repository.create(clientId, member.id(), request.contractId(), request.contractType(), request.amount(),
                request.header().idempotencyKey(), fingerprint, encryption.encrypt(request.depositorName()),
                request.withdrawalBankCode(), encryption.encrypt(request.withdrawalAccountNo()));
        DepositEscrow created = repository.findByIdempotencyKey(clientId, request.header().idempotencyKey())
                .or(() -> repository.findByContractId(clientId, request.contractId()))
                .orElseThrow(() -> error(HttpStatus.SERVICE_UNAVAILABLE, "Q1000",
                        "에스크로 생성 결과를 확인할 수 없습니다.", request.header()));
        repository.pending(created.escrowId());
        return reload(created);
    }

    private void execute(DepositEscrow escrow, String payerUserKey, EscrowRequestHeader header) {
        DepositEscrow current = resumable(escrow);
        if (current == null) return;
        try {
            current = createVirtualAccount(current);
            current = beginTransfer(current);
            current = transfer(current, payerUserKey);
            current = verify(current);
            current = settle(current);
            if (current != null && SETTLED.equals(current.depositStep())) closeBestEffort(current);
        } catch (FinanceApiException exception) {
            failCurrentAttempt(escrow, exception.code());
            repository.failed(escrow.escrowId(), escrow.transactionId(), exception.code());
            throw error(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), header);
        } catch (FinanceClientUnavailableException exception) {
            unknownCurrentAttempt(escrow);
            repository.unknown(escrow.escrowId(), escrow.transactionId());
        }
    }

    private DepositEscrow resumable(DepositEscrow escrow) {
        DepositEscrow current = escrow;
        if (COMPLETED.equals(current.transactionStatus())) {
            if (SETTLED.equals(current.depositStep())) closeBestEffort(current);
            return null;
        }
        if ("FAILED".equals(current.transactionStatus())) return null;
        if (!UNKNOWN.equals(current.transactionStatus())) return current;
        recoverUnknown(current);
        current = reload(current);
        if (UNKNOWN.equals(current.transactionStatus())) return null;
        if (COMPLETED.equals(current.transactionStatus())) {
            if (SETTLED.equals(current.depositStep())) closeBestEffort(current);
            return null;
        }
        return current;
    }

    private DepositEscrow createVirtualAccount(DepositEscrow current) {
        if (current == null || !"VA_CREATING".equals(current.depositStep())) return current;
        Operation attempt = claim(current.transactionId(), "VA_CREATE");
        if (attempt == null) return null;
        FinanceClient.VirtualAccount virtual = finance.createVirtualAccount(
                properties.escrowUserKey(), properties.escrowAccountNo(),
                encryption.decrypt(current.depositorNameEncrypted()), current.amount(),
                LocalDate.now(clock.withZone(financeZone)).plusDays(properties.virtualAccountValidityDays()),
                attempt.reference());
        repository.attemptSucceeded(attempt.id(), virtual.virtualAccountId());
        repository.virtualAccountCreated(current.transactionId(), virtual.virtualAccountId(), virtual.virtualAccountNo());
        return reload(current);
    }

    private DepositEscrow beginTransfer(DepositEscrow current) {
        if (current == null || !"VA_CREATED".equals(current.depositStep())) return current;
        repository.transferStarted(current.transactionId());
        return reload(current);
    }

    private DepositEscrow transfer(DepositEscrow current, String payerUserKey) {
        if (current == null || !"TRANSFER_SENDING".equals(current.depositStep())) return current;
        Operation attempt = claim(current.transactionId(), "DEPOSIT_TRANSFER");
        if (attempt == null) return null;
        String depositor = encryption.decrypt(current.depositorNameEncrypted());
        String transferId = finance.transfer(payerUserKey, current.virtualAccountNo(),
                encryption.decrypt(current.sourceAccountEncrypted()), current.amount(), depositor,
                "KOMIT-" + current.escrowId(), attempt.reference());
        repository.attemptSucceeded(attempt.id(), transferId);
        repository.transferred(current.transactionId(), transferId);
        return reload(current);
    }

    private DepositEscrow verify(DepositEscrow current) {
        if (current == null || !"TRANSFERRED".equals(current.depositStep())) return current;
        Operation attempt = claim(current.transactionId(), "DEPOSIT_VERIFY");
        if (attempt == null) return null;
        List<FinanceClient.VirtualAccountDeposit> deposits = finance.inquireVirtualAccountDeposits(
                properties.escrowUserKey(), current.virtualAccountId());
        BigInteger amount = deposits.stream().map(FinanceClient.VirtualAccountDeposit::amount)
                .reduce(ZERO, BigInteger::add);
        boolean nameMatched = !deposits.isEmpty()
                && deposits.stream().allMatch(FinanceClient.VirtualAccountDeposit::nameMatched);
        boolean matched = current.amount().equals(amount) && nameMatched;
        repository.attemptSucceeded(attempt.id(), current.virtualAccountId());
        repository.verified(current.transactionId(), amount, nameMatched, matched);
        if (matched) return reload(current);
        repository.failed(current.escrowId(), current.transactionId(), AMOUNT_MISMATCH);
        return null;
    }

    private DepositEscrow settle(DepositEscrow current) {
        if (current == null || !"VERIFIED".equals(current.depositStep())) return current;
        Operation attempt = claim(current.transactionId(), "VA_SETTLE");
        if (attempt == null) return null;
        FinanceClient.VirtualAccountSettlement result = finance.settleVirtualAccount(
                properties.escrowUserKey(), current.virtualAccountId(), attempt.reference());
        if (!current.amount().equals(result.settledAmount())) {
            repository.attemptFailed(attempt.id(), AMOUNT_MISMATCH);
            repository.failed(current.escrowId(), current.transactionId(), AMOUNT_MISMATCH);
            return null;
        }
        repository.attemptSucceeded(attempt.id(), current.virtualAccountId());
        repository.settled(current.escrowId(), current.transactionId(), result.settledAmount());
        return reload(current);
    }

    private void closeBestEffort(DepositEscrow escrow) {
        Operation attempt = claim(escrow.transactionId(), "VA_CLOSE");
        if (attempt == null) return;
        try {
            String status = finance.closeVirtualAccount(properties.escrowUserKey(), escrow.virtualAccountId(),
                    attempt.reference());
            repository.attemptSucceeded(attempt.id(), status);
            if ("CLOSED".equals(status)) repository.closed(escrow.transactionId());
        } catch (RuntimeException exception) {
            repository.attemptUnknown(attempt.id());
        }
    }

    private void recoverUnknown(DepositEscrow escrow) {
        if (escrow.virtualAccountId() == null) return;
        Operation attempt = claim(escrow.transactionId(), "RESULT_QUERY");
        if (attempt == null) return;
        try {
            List<FinanceClient.VirtualAccountDeposit> deposits = finance.inquireVirtualAccountDeposits(
                    properties.escrowUserKey(), escrow.virtualAccountId());
            BigInteger amount = deposits.stream().map(FinanceClient.VirtualAccountDeposit::amount)
                    .reduce(ZERO, BigInteger::add);
            boolean nameMatched = !deposits.isEmpty()
                    && deposits.stream().allMatch(FinanceClient.VirtualAccountDeposit::nameMatched);
            boolean settled = !deposits.isEmpty()
                    && deposits.stream().allMatch(FinanceClient.VirtualAccountDeposit::transferred);
            if (!escrow.amount().equals(amount) || !nameMatched) {
                repository.attemptUnknown(attempt.id());
                return;
            }
            repository.attemptSucceeded(attempt.id(), escrow.virtualAccountId());
            if (settled) repository.recoveredSettlement(escrow.escrowId(), escrow.transactionId(), amount);
            else repository.recoveredTransfer(escrow.transactionId(), amount, true);
        } catch (RuntimeException exception) {
            repository.attemptUnknown(attempt.id());
        }
    }

    public EscrowDtos.CancelRecord cancel(EscrowDtos.CancelRequest request) {
        AuthenticatedCaller.Client caller = client(request.header(), "cancelEscrow", ApiRequirements.write(false));
        String fingerprint = fingerprints.create(request);
        EscrowDepositRepository.CancelCommand previous = repository.findCancelCommand(
                caller.serviceClientId(), request.header().idempotencyKey()).orElse(null);
        if (previous != null) {
            if (previous.escrowId() != request.escrowId() || !previous.fingerprint().equals(fingerprint)) {
                throw error(HttpStatus.BAD_REQUEST, "E5003",
                    "같은 멱등키로 다른 요청이 접수되었습니다.", request.header());
            }
            return new EscrowDtos.CancelRecord(request.escrowId(), "CANCELLED");
        }
        DepositEscrow escrow = repository.findById(caller.serviceClientId(), request.escrowId())
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "E5004", "에스크로를 찾을 수 없습니다.", request.header()));
        if ("ACTIVE".equals(escrow.escrowStatus()) || COMPLETED.equals(escrow.transactionStatus())) {
            throw error(HttpStatus.BAD_REQUEST, AMOUNT_MISMATCH, "이미 예치가 끝나 취소할 수 없습니다.", request.header());
        }
        boolean cancelled = repository.cancel(caller.serviceClientId(), request.escrowId(),
                request.header().idempotencyKey(), fingerprint);
        if (!cancelled) throw error(HttpStatus.BAD_REQUEST, "E5015",
                "예치 송금 결과를 확인하기 전에는 취소할 수 없습니다.", request.header());
        return new EscrowDtos.CancelRecord(request.escrowId(), "CANCELLED");
    }

    @Transactional(readOnly = true)
    public EscrowDtos.InquireRecord inquire(EscrowDtos.InquireRequest request) {
        AuthenticatedCaller.Client caller = client(request.header(), "inquireEscrow", ApiRequirements.read());
        DepositEscrow escrow = repository.findById(caller.serviceClientId(), request.escrowId())
                .filter(row -> request.contractId() == null || request.contractId().equals(row.contractId()))
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "E5004", "에스크로를 찾을 수 없습니다.", request.header()));
        return detail(escrow);
    }

    @Transactional(readOnly = true)
    public EscrowDtos.ListRecord list(EscrowDtos.InquireListRequest request) {
        AuthenticatedCaller.Client caller = client(request.header(), "inquireEscrowList", ApiRequirements.read());
        List<EscrowDtos.SummaryItem> items = repository.list(caller.serviceClientId(), request.contractIds(), request.status())
                .stream().map(row -> new EscrowDtos.SummaryItem(row.escrowId(), row.contractId(),
                        row.escrowStatus(), completed(row) ? row.amount() : ZERO, held(row))).toList();
        return new EscrowDtos.ListRecord(items.size(), items);
    }

    private EscrowDtos.InquireRecord detail(DepositEscrow escrow) {
        BigInteger deposited = completed(escrow) ? escrow.amount() : ZERO;
        var totals = settlements.totals(escrow.escrowId());
        return new EscrowDtos.InquireRecord(escrow.escrowId(), escrow.contractId(), escrow.escrowStatus(),
                deposited, totals.released(), totals.fee(), totals.refunded(),
                deposited.subtract(totals.released()).subtract(totals.fee()).subtract(totals.refunded()),
                held(escrow), repository.transactions(escrow.escrowId()));
    }

    private EscrowDtos.AmountRecord amount(DepositEscrow escrow) {
        BigInteger deposited = completed(escrow) ? escrow.amount() : ZERO;
        return new EscrowDtos.AmountRecord(escrow.escrowId(), escrow.escrowStatus(), deposited,
                deposited, escrow.transactionId());
    }

    private boolean completed(DepositEscrow escrow) {
        return COMPLETED.equals(escrow.transactionStatus());
    }

    private boolean held(DepositEscrow escrow) {
        return "HELD".equals(escrow.holdStatus());
    }

    private Operation claim(long transactionId, String step) {
        String reference = nextReference();
        long attempt = repository.prepareAttempt(transactionId, step, reference);
        return repository.claimAttempt(attempt, instanceId)
                ? new Operation(attempt, repository.attemptReference(attempt)) : null;
    }

    private DepositEscrow reload(DepositEscrow escrow) {
        return repository.findById(escrow.serviceClientId(), escrow.escrowId()).orElseThrow();
    }

    private String nextReference() {
        return REFERENCE_TIME.format(clock.instant().atZone(financeZone)) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private void failCurrentAttempt(DepositEscrow escrow, String code) {
        // The claimed row is the only DISPATCHING attempt for this transaction.
        jdbcAttemptState(escrow.transactionId(), "FAILED", code);
    }

    private void unknownCurrentAttempt(DepositEscrow escrow) {
        jdbcAttemptState(escrow.transactionId(), UNKNOWN, null);
    }

    private void jdbcAttemptState(long transactionId, String status, String code) {
        repository.finishDispatching(transactionId, status, code);
    }

    private void requireFingerprint(DepositEscrow escrow, String fingerprint, EscrowRequestHeader header) {
        if (!escrow.requestFingerprint().equals(fingerprint)) {
            throw error(HttpStatus.BAD_REQUEST, "E5003", "기존 계약의 예치 요청과 다릅니다.", header);
        }
    }

    private AuthenticatedCaller.Client client(EscrowRequestHeader header, String apiName, ApiRequirements requirements) {
        return (AuthenticatedCaller.Client) security.authenticate(header, apiName, requirements);
    }

    private static EscrowApiException error(HttpStatus status, String code, String message, EscrowRequestHeader header) {
        return new EscrowApiException(status, code, message, header);
    }

    private record Operation(long id, String reference) {
    }
}
