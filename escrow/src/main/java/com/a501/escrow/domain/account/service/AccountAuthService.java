package com.a501.escrow.domain.account.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import com.a501.escrow.common.api.EscrowRequestHeader;
import com.a501.escrow.common.crypto.FieldEncryption;
import com.a501.escrow.common.crypto.SensitiveDataHasher;
import com.a501.escrow.common.exception.EscrowApiException;
import com.a501.escrow.common.security.ApiRequirements;
import com.a501.escrow.common.security.AuthenticatedCaller;
import com.a501.escrow.common.security.InternalApiSecurity;
import com.a501.escrow.common.security.ServiceClientRepository;
import com.a501.escrow.domain.account.dto.AccountAuthDtos;
import com.a501.escrow.domain.account.repository.AccountAuthRepository;
import com.a501.escrow.domain.account.repository.AccountAuthRequest;
import com.a501.escrow.integration.finance.FinanceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthService {

    private static final String INVALID_ACCOUNT = "0000000000000000";
    // R-ACC-02: 1원 인증 유효 시간.
    private static final long AUTH_VALID_MINUTES = 10;

    private final InternalApiSecurity security;
    private final AccountAuthRepository repository;
    private final ServiceClientRepository clientRepository;
    private final SensitiveDataHasher hasher;
    private final FieldEncryption encryption;
    private final FinanceClient financeClient;
    private final Clock clock;
    private final String instanceId;

    public AccountAuthService(
            InternalApiSecurity security,
            AccountAuthRepository repository,
            ServiceClientRepository clientRepository,
            SensitiveDataHasher hasher,
            FieldEncryption encryption,
            FinanceClient financeClient,
            Clock clock,
            @Value("${POD_NAME:local-writer}") String instanceId
    ) {
        this.security = security;
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.hasher = hasher;
        this.encryption = encryption;
        this.financeClient = financeClient;
        this.clock = clock;
        this.instanceId = instanceId;
    }

    public AccountAuthDtos.OpenRecord open(AccountAuthDtos.OpenRequest request) {
        AuthenticatedCaller.Client client = client(request.header(), "openAccountAuth", ApiRequirements.write(true));
        rejectInvalidAccount(request.accountNo(), request.header());
        String key = request.header().idempotencyKey();
        String fingerprint = hasher.accountAuthFingerprint(
                client.serviceClientId(), request.header().userKey(), request.accountNo());

        AccountAuthRequest auth = repository.findByIdempotencyKey(client.serviceClientId(), key)
                .orElseGet(() -> createRequest(client, request, fingerprint));
        if (!auth.requestFingerprint().equals(fingerprint)) {
            fail(HttpStatus.BAD_REQUEST, "E5003", "같은 멱등키로 다른 요청이 접수되었습니다.", request.header());
        }
        if ("SUCCEEDED".equals(auth.status())) {
            return result(auth);
        }
        if (!repository.claim(auth.id(), instanceId)) {
            throw pending(request.header());
        }

        try {
            FinanceClient.AccountAuthTransfer transfer = financeClient.openAccountAuth(
                    request.header().userKey(), request.accountNo(),
                    clientRepository.findOneWonAuthMessage(client.serviceClientId()));
            if (transfer.transactionUniqueNo() == null || transfer.transactionUniqueNo().isBlank()) {
                throw new IllegalStateException("금융망 인증 거래번호가 없습니다.");
            }
            OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusMinutes(AUTH_VALID_MINUTES);
            repository.succeed(auth.id(), transfer.transactionUniqueNo(), expiresAt);
            return new AccountAuthDtos.OpenRecord(
                    transfer.transactionUniqueNo(), request.accountNo(), expiresAt.toString());
        } catch (RuntimeException exception) {
            repository.markUnknown(auth.id());
            throw pending(request.header());
        }
    }

    public AccountAuthDtos.CheckRecord check(AccountAuthDtos.CheckRequest request) {
        AuthenticatedCaller.Client client = client(request.header(), "checkAuthCode", ApiRequirements.read());
        rejectInvalidAccount(request.accountNo(), request.header());
        String userHash = hasher.userKeyLookupHash(client.serviceClientId(), request.header().userKey());
        AccountAuthRequest auth = repository
                .findByTransactionNo(client.serviceClientId(), request.transactionUniqueNo())
                .filter(found -> "SUCCEEDED".equals(found.status()))
                .filter(found -> found.userKeyLookupHash().equals(userHash))
                .filter(found -> encryption.decrypt(found.accountNoEncrypted()).equals(request.accountNo()))
                .orElseThrow(() -> api(HttpStatus.BAD_REQUEST, "E5012", "인증 요청을 찾을 수 없습니다.", request.header()));
        if (!auth.expiresAt().isAfter(OffsetDateTime.now(clock))) {
            fail(HttpStatus.BAD_REQUEST, "E5011", "인증 요청이 만료되었습니다.", request.header());
        }
        return new AccountAuthDtos.CheckRecord("SUCCESS", request.transactionUniqueNo(), request.accountNo());
    }

    public AccountAuthDtos.HistoryRecord history(AccountAuthDtos.HistoryRequest request) {
        AuthenticatedCaller.Client client = client(
                request.header(), "inquireTransactionHistoryList", ApiRequirements.read());
        rejectInvalidAccount(request.accountNo(), request.header());
        requireKnownAccount(client.serviceClientId(), request.header(), request.accountNo());
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(request.startDate(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            endDate = LocalDate.parse(request.endDate(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            throw api(HttpStatus.BAD_REQUEST, "Q1001", "조회 기간이 올바르지 않습니다.", request.header());
        }
        List<AccountAuthDtos.HistoryItem> items;
        try {
            items = financeClient.inquireTransactions(
                            request.header().userKey(), request.accountNo(), startDate, endDate,
                            request.transactionType(), request.orderByType())
                    .stream().map(item -> new AccountAuthDtos.HistoryItem(
                            item.transactionUniqueNo(), item.transactionDate(), item.transactionTime(),
                            item.transactionType(), item.transactionTypeName(), item.transactionBalance(),
                            item.transactionAfterBalance(), item.transactionSummary())).toList();
        } catch (RuntimeException exception) {
            throw pending(request.header());
        }
        return new AccountAuthDtos.HistoryRecord(items.size(), items);
    }

    private AccountAuthRequest createRequest(
            AuthenticatedCaller.Client client,
            AccountAuthDtos.OpenRequest request,
            String fingerprint
    ) {
        repository.insertRequested(
                client.serviceClientId(), request.header().idempotencyKey(), fingerprint,
                hasher.userKeyLookupHash(client.serviceClientId(), request.header().userKey()),
                encryption.encrypt(request.header().userKey()), encryption.encrypt(request.accountNo()),
                clientRepository.findOneWonAuthMessage(client.serviceClientId()), UUID.randomUUID().toString());
        return repository.findByIdempotencyKey(client.serviceClientId(), request.header().idempotencyKey())
                .orElseThrow();
    }

    private AccountAuthDtos.OpenRecord result(AccountAuthRequest auth) {
        return new AccountAuthDtos.OpenRecord(
                auth.transactionUniqueNo(), encryption.decrypt(auth.accountNoEncrypted()),
                auth.expiresAt().toString());
    }

    private void requireKnownAccount(long clientId, EscrowRequestHeader header, String accountNo) {
        String hash = hasher.userKeyLookupHash(clientId, header.userKey());
        boolean known = repository.findByUserHash(clientId, hash).stream()
                .filter(auth -> "SUCCEEDED".equals(auth.status()))
                .anyMatch(auth -> encryption.decrypt(auth.accountNoEncrypted()).equals(accountNo));
        if (!known) {
            fail(HttpStatus.BAD_REQUEST, "E5012", "인증 요청을 찾을 수 없습니다.", header);
        }
    }

    private AuthenticatedCaller.Client client(EscrowRequestHeader header, String api, ApiRequirements requirements) {
        return (AuthenticatedCaller.Client) security.authenticate(header, api,
                new ApiRequirements(requirements.idempotencyKeyRequired(), true,
                        requirements.writerRequired(), false));
    }

    private static void rejectInvalidAccount(String accountNo, EscrowRequestHeader header) {
        if (INVALID_ACCOUNT.equals(accountNo)) {
            fail(HttpStatus.BAD_REQUEST, "A1003", "계좌번호가 유효하지 않습니다.", header);
        }
    }

    private static EscrowApiException pending(EscrowRequestHeader header) {
        return api(HttpStatus.SERVICE_UNAVAILABLE, "Q1000",
                "인증 요청 결과를 확인 중입니다. 같은 멱등키로 다시 확인해 주세요.", header);
    }

    private static EscrowApiException api(HttpStatus status, String code, String message, EscrowRequestHeader header) {
        return new EscrowApiException(status, code, message, header);
    }

    private static void fail(HttpStatus status, String code, String message, EscrowRequestHeader header) {
        throw api(status, code, message, header);
    }
}
