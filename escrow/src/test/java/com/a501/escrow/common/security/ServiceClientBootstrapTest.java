package com.a501.escrow.common.security;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.escrow.common.crypto.ClientApiKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ServiceClientBootstrapTest {

    private final NamedParameterJdbcTemplate jdbcTemplate =
            org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    private final ClientApiKeyHasher apiKeyHasher =
            org.mockito.Mockito.mock(ClientApiKeyHasher.class);

    @Test
    void skipsRegistrationWhenBootstrapKeyIsEmpty() {
        ServiceClientBootstrap bootstrap = new ServiceClientBootstrap(
                jdbcTemplate, apiKeyHasher, "", "");

        bootstrap.run(new DefaultApplicationArguments());

        verify(apiKeyHasher, never()).hash(anyString());
        verify(jdbcTemplate, never()).update(anyString(), anyMap());
    }

    @Test
    void storesOnlyHashedKeyAndInsertsMissingClient() {
        when(apiKeyHasher.hash("backend-secret")).thenReturn("hashed-key");
        when(jdbcTemplate.update(anyString(), anyMap()))
                .thenReturn(0, 1);
        ServiceClientBootstrap bootstrap = new ServiceClientBootstrap(
                jdbcTemplate, apiKeyHasher, "backend-secret", "KOMIT");

        bootstrap.run(new DefaultApplicationArguments());

        verify(apiKeyHasher).hash("backend-secret");
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(anyString(), anyMap());
    }

    @Test
    void doesNotInsertWhenExistingClientWasUpdated() {
        when(apiKeyHasher.hash("backend-secret")).thenReturn("hashed-key");
        when(jdbcTemplate.update(anyString(), anyMap())).thenReturn(1);
        ServiceClientBootstrap bootstrap = new ServiceClientBootstrap(
                jdbcTemplate, apiKeyHasher, "backend-secret", "KOMIT");

        bootstrap.run(new DefaultApplicationArguments());

        verify(jdbcTemplate).update(anyString(), anyMap());
    }

    @Test
    void rejectsEmptyAuthMessageWhenKeyIsConfigured() {
        ServiceClientBootstrap bootstrap = new ServiceClientBootstrap(
                jdbcTemplate, apiKeyHasher, "backend-secret", "");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> bootstrap.run(new DefaultApplicationArguments()));
    }
}
