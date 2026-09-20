package com.a501.escrow.integration.finance;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinanceClient {

    UserRegistration registerUser(String userId);

    Optional<UserRegistration> findUser(String userId);

    DemandDepositAccount openDemandDepositAccount(String userKey, String accountTypeUniqueNo);

    List<DemandDepositAccount> findDemandDepositAccounts(String userKey);

    AccountHolder inquireAccountHolder(String userKey, String accountNo);

    String depositSeedMoney(
            String userKey,
            String accountNo,
            BigInteger amount,
            String requestReference);

    List<DepositTransaction> findDeposits(
            String userKey,
            String accountNo,
            LocalDate startDate,
            LocalDate endDate);

    AccountAuthTransfer openAccountAuth(String userKey, String accountNo, String authText);

    List<AccountTransaction> inquireTransactions(
            String userKey,
            String accountNo,
            LocalDate startDate,
            LocalDate endDate,
            String transactionType,
            String orderByType);

    VirtualAccount createVirtualAccount(String userKey, String mainAccountNo, String depositorName,
            BigInteger expectedAmount, LocalDate expiryDate, String requestReference);

    String transfer(String userKey, String depositAccountNo, String withdrawalAccountNo,
            BigInteger amount, String depositSummary, String withdrawalSummary, String requestReference);

    List<VirtualAccountDeposit> inquireVirtualAccountDeposits(String userKey, String virtualAccountId);

    VirtualAccountSettlement settleVirtualAccount(String userKey, String virtualAccountId,
            String requestReference);

    String closeVirtualAccount(String userKey, String virtualAccountId, String requestReference);

    record UserRegistration(String userId, String userKey) {
    }

    record DemandDepositAccount(
            String bankCode,
            String accountNo,
            String accountName,
            String accountTypeCode,
            String currency
    ) {
    }

    record AccountHolder(String bankCode, String accountNo, String userName) {
    }

    record DepositTransaction(
            String transactionUniqueNo,
            BigInteger amount,
            String summary
    ) {
    }

    record AccountAuthTransfer(String transactionUniqueNo) {
    }

    record AccountTransaction(
            String transactionUniqueNo,
            String transactionDate,
            String transactionTime,
            String transactionType,
            String transactionTypeName,
            String transactionBalance,
            String transactionAfterBalance,
            String transactionSummary
    ) {
    }

    record VirtualAccount(String virtualAccountId, String virtualAccountNo) {
    }

    record VirtualAccountDeposit(BigInteger amount, boolean nameMatched, boolean transferred) {
    }

    record VirtualAccountSettlement(BigInteger settledAmount, String settledAt) {
    }
}
