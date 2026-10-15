package com.a501.escrow.domain.member.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import com.a501.escrow.domain.member.dto.MemberAccountDtos;
import com.a501.escrow.domain.member.repository.MemberAccountProvision;
import com.a501.escrow.domain.member.repository.MemberAccountRepository;
import com.a501.escrow.integration.finance.FinanceClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MemberAccountService {
    private static final String BANK_CODE = "001"; // R-ACC-10
    private static final String ACCOUNT_NAME = "Komit 데모 입출금"; // R-ACC-10
    private static final long SEED_AMOUNT = 50_000_000L; // R-PAY-10
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InternalApiSecurity security;
    private final MemberAccountRepository repository;
    private final RequestFingerprint fingerprint;
    private final SensitiveDataHasher hasher;
    private final FieldEncryption encryption;
    private final FinanceClient finance;
    private final FinanceProperties properties;
    private final Clock clock;

    public MemberAccountService(InternalApiSecurity security, MemberAccountRepository repository,
            RequestFingerprint fingerprint, SensitiveDataHasher hasher, FieldEncryption encryption,
            FinanceClient finance, FinanceProperties properties, Clock clock) {
        this.security = security;
        this.repository = repository;
        this.fingerprint = fingerprint;
        this.hasher = hasher;
        this.encryption = encryption;
        this.finance = finance;
        this.properties = properties;
        this.clock = clock;
    }

    public MemberAccountDtos.CreateRecord create(MemberAccountDtos.CreateRequest request) {
        AuthenticatedCaller.Client caller = (AuthenticatedCaller.Client) security.authenticate(
                request.header(), "createMemberAccount", ApiRequirements.write(false));
        String requestFingerprint = fingerprint.create(request);
        MemberAccountProvision provision = repository.findByKey(caller.serviceClientId(), request.header().idempotencyKey())
                .orElseGet(() -> getOrCreate(caller.serviceClientId(), request, requestFingerprint));
        if (!provision.requestFingerprint().equals(requestFingerprint)) {
            throw error("E5003", "같은 멱등 키로 다른 요청이 접수되었습니다.", request.header());
        }

        for (int step = 0; step < 4; step++) {
            if ("COMPLETED".equals(provision.provisionStep())) {
                return result(provision);
            }
            if ("UNKNOWN".equals(provision.provisionStatus())) {
                try {
                    recover(provision, request.header());
                } catch (EscrowApiException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw pending(request.header());
                }
            } else {
                execute(provision, request.header());
            }
            provision = repository.findByUser(caller.serviceClientId(), request.userId()).orElseThrow();
        }
        throw pending(request.header());
    }

    private MemberAccountProvision getOrCreate(long clientId, MemberAccountDtos.CreateRequest request, String hash) {
        repository.insert(clientId, request.userId(), properties.accountTypeUniqueNo(),
                request.header().idempotencyKey(), hash, SEED_AMOUNT);
        return repository.findByUser(clientId, request.userId()).orElseThrow();
    }

    private void execute(MemberAccountProvision provision, EscrowRequestHeader header) {
        String step = provision.provisionStep();
        if ("SEED_DEPOSIT".equals(step)) {
            executeSeed(provision, header);
            return;
        }
        if (!repository.claim(provision.id(), step)) {
            throw pending(header);
        }
        try {
            if ("USER_REGISTER".equals(step)) {
                saveUser(provision, finance.registerUser(provision.userId()));
            } else if ("ACCOUNT_OPEN".equals(step)) {
                String userKey = encryption.decrypt(provision.userKeyEncrypted());
                saveAccount(provision, finance.openDemandDepositAccount(userKey, provision.accountTypeUniqueNo()));
            }
        } catch (RuntimeException exception) {
            repository.markUnknown(provision.id(), step);
            throw pending(header);
        }
    }

    private void executeSeed(MemberAccountProvision provision, EscrowRequestHeader header) {
        String reference = requestReference();
        if (!repository.claimSeed(provision.id(), reference)) {
            throw pending(header);
        }
        try {
            String transactionNo = finance.depositSeedMoney(encryption.decrypt(provision.userKeyEncrypted()),
                    encryption.decrypt(provision.accountNoEncrypted()), BigInteger.valueOf(SEED_AMOUNT), reference);
            if (transactionNo == null || transactionNo.isBlank()) {
                throw new IllegalStateException("금융망 입금 거래번호가 없습니다.");
            }
            repository.complete(provision.id(), transactionNo);
        } catch (RuntimeException exception) {
            repository.markUnknown(provision.id(), "SEED_DEPOSIT");
            throw pending(header);
        }
    }

    private void recover(MemberAccountProvision provision, EscrowRequestHeader header) {
        switch (provision.provisionStep()) {
            case "USER_REGISTER" -> finance.findUser(provision.userId())
                    .ifPresentOrElse(user -> saveUser(provision, user), () -> { throw pending(header); });
            case "ACCOUNT_OPEN" -> {
                List<FinanceClient.DemandDepositAccount> candidates = finance.findDemandDepositAccounts(
                                encryption.decrypt(provision.userKeyEncrypted())).stream()
                        .filter(this::isConfiguredAccount).toList();
                if (candidates.size() != 1) throw pending(header);
                saveAccount(provision, candidates.getFirst());
            }
            case "SEED_DEPOSIT" -> {
                LocalDate date = provision.stepRequestedAt().atZoneSameInstant(clock.getZone()).toLocalDate();
                List<FinanceClient.DepositTransaction> candidates = finance.findDeposits(
                                encryption.decrypt(provision.userKeyEncrypted()),
                                encryption.decrypt(provision.accountNoEncrypted()), date, LocalDate.now(clock)).stream()
                        .filter(item -> BigInteger.valueOf(provision.seedAmount()).equals(item.amount()))
                        .filter(item -> provision.seedRequestReference().equals(item.summary())).toList();
                if (candidates.size() != 1) throw pending(header);
                repository.complete(provision.id(), candidates.getFirst().transactionUniqueNo());
            }
            default -> throw pending(header);
        }
    }

    private void saveUser(MemberAccountProvision provision, FinanceClient.UserRegistration user) {
        if (!provision.userId().equals(user.userId()) || user.userKey() == null || user.userKey().isBlank()) {
            throw new IllegalStateException("금융망 회원 발급 결과가 요청과 일치하지 않습니다.");
        }
        repository.userRegistered(provision.id(), encryption.encrypt(user.userKey()),
                hasher.userKeyLookupHash(provision.serviceClientId(), user.userKey()));
    }

    private void saveAccount(MemberAccountProvision provision, FinanceClient.DemandDepositAccount account) {
        if (!matchesConfiguredProduct(account)) {
            throw new IllegalStateException("설정한 입출금 상품과 다른 계좌가 반환되었습니다.");
        }
        repository.accountOpened(provision.id(), account.bankCode(), encryption.encrypt(account.accountNo()));
    }

    /**
     * 계좌 목록에서 발급 대상을 고를 때 쓰는 기준. 목록 항목은 상품 속성을 모두 담으므로
     * 네 가지를 다 본다. (R-ACC-10)
     */
    private boolean isConfiguredAccount(FinanceClient.DemandDepositAccount account) {
        return BANK_CODE.equals(account.bankCode()) && ACCOUNT_NAME.equals(account.accountName())
                && "1".equals(account.accountTypeCode()) && "KRW".equals(account.currency());
    }

    /**
     * 받은 계좌가 설정한 상품인지 본다. 개설 응답은 상품명·계좌종류·통화를 담지 않을 수
     * 있으므로, 담겨 온 값만 대조한다. 없는 값을 틀렸다고 보면 정상 개설이 모두 막힌다.
     */
    private boolean matchesConfiguredProduct(FinanceClient.DemandDepositAccount account) {
        return BANK_CODE.equals(account.bankCode())
                && matchesIfPresent(account.accountName(), ACCOUNT_NAME)
                && matchesIfPresent(account.accountTypeCode(), "1")
                && matchesIfPresent(account.currency(), "KRW");
    }

    private static boolean matchesIfPresent(String value, String expected) {
        return value == null || value.isBlank() || expected.equals(value);
    }

    private MemberAccountDtos.CreateRecord result(MemberAccountProvision provision) {
        return new MemberAccountDtos.CreateRecord(provision.userId(), encryption.decrypt(provision.userKeyEncrypted()),
                provision.bankCode(), encryption.decrypt(provision.accountNoEncrypted()));
    }

    private String requestReference() {
        String prefix = OffsetDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static EscrowApiException pending(EscrowRequestHeader header) {
        return new EscrowApiException(HttpStatus.SERVICE_UNAVAILABLE, "Q1000",
                "계좌 발급 결과를 확인 중입니다. 같은 멱등 키로 다시 확인해 주세요.", header);
    }

    private static EscrowApiException error(String code, String message, EscrowRequestHeader header) {
        return new EscrowApiException(HttpStatus.BAD_REQUEST, code, message, header);
    }
}
