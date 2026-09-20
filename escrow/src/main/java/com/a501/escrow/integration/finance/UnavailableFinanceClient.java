package com.a501.escrow.integration.finance;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class UnavailableFinanceClient implements FinanceClient {

    private static FinanceClientUnavailableException unavailable() {
        return new FinanceClientUnavailableException("SSAFY 금융 클라이언트가 구성되지 않았습니다.");
    }

    @Override public UserRegistration registerUser(String userId) { throw unavailable(); }
    @Override public Optional<UserRegistration> findUser(String userId) { throw unavailable(); }
    @Override public DemandDepositAccount openDemandDepositAccount(String userKey, String product) { throw unavailable(); }
    @Override public List<DemandDepositAccount> findDemandDepositAccounts(String userKey) { throw unavailable(); }
    @Override public AccountHolder inquireAccountHolder(String userKey, String accountNo) { throw unavailable(); }
    @Override public String depositSeedMoney(String userKey, String accountNo, BigInteger amount, String reference) { throw unavailable(); }
    @Override public List<DepositTransaction> findDeposits(String userKey, String accountNo, LocalDate start, LocalDate end) { throw unavailable(); }
    @Override public AccountAuthTransfer openAccountAuth(String userKey, String accountNo, String authText) { throw unavailable(); }
    @Override public List<AccountTransaction> inquireTransactions(String userKey, String accountNo, LocalDate start, LocalDate end, String type, String order) { throw unavailable(); }
    @Override public VirtualAccount createVirtualAccount(String userKey, String mainAccountNo, String depositorName, BigInteger expectedAmount, LocalDate expiryDate, String reference) { throw unavailable(); }
    @Override public String transfer(String userKey, String depositAccountNo, String withdrawalAccountNo, BigInteger amount, String depositSummary, String withdrawalSummary, String reference) { throw unavailable(); }
    @Override public List<VirtualAccountDeposit> inquireVirtualAccountDeposits(String userKey, String virtualAccountId) { throw unavailable(); }
    @Override public VirtualAccountSettlement settleVirtualAccount(String userKey, String virtualAccountId, String reference) { throw unavailable(); }
    @Override public String closeVirtualAccount(String userKey, String virtualAccountId, String reference) { throw unavailable(); }
}
