package com.a501.service.domain.account.client;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.AccountErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EscrowAccountClientTest {
    @Test
    void readsSuccessResponsesForAccountProvisionAndVerification() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EscrowAccountClient client = new EscrowAccountClient(builder, "http://localhost:8081", "test-key");

        server.expect(requestTo("http://localhost:8081/api/v1/escrow/createMemberAccount"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"userKey":"user-key","bankCode":"001","accountNo":"0015337596722826"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8081/api/v1/edu/accountAuth/openAccountAuth"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"transactionUniqueNo":"tx-1","expiresAt":"2026-09-21T10:00:00+09:00"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:8081/api/v1/edu/accountAuth/checkAuthCode"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"status":"SUCCESS"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.createMemberAccount("member-1", "provision-1"))
                .isEqualTo(new EscrowAccountClient.MemberAccount("user-key", "001", "0015337596722826"));
        assertThat(client.openAccountAuth("user-key", "0015337596722826", "auth-1"))
                .isEqualTo(new EscrowAccountClient.OpenedAuth("tx-1", "2026-09-21T10:00:00+09:00"));
        client.checkAuthCode("user-key", "tx-1", "0015337596722826", "1234");
        server.verify();
    }

    @Test
    void mapsEscrowErrorResponseAfterReadingItsCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EscrowAccountClient client = new EscrowAccountClient(builder, "http://localhost:8081", "test-key");
        server.expect(requestTo("http://localhost:8081/api/v1/edu/accountAuth/openAccountAuth"))
                .andRespond(withBadRequest().body("""
                        {"Header":{"responseCode":"A1003"}}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.openAccountAuth("user-key", "0015337596722826", "auth-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_REJECTED);
        server.verify();
    }
}
