package com.a501.service.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.account.AccountErrorCode;
import com.a501.service.domain.account.service.MemberAccountProvisioningService;
import com.a501.service.domain.auth.SignupErrorCode;
import com.a501.service.domain.setting.dto.response.RequiredTermsResponse;
import com.a501.service.domain.setting.service.TermsAgreementService;
import com.a501.service.domain.user.dto.UserSnapshot;
import com.a501.service.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {
    @Mock UserService users;
    @Mock TermsAgreementService terms;
    @Mock MemberAccountProvisioningService provisioning;
    private SignupService service;

    @BeforeEach
    void setUp() { service = new SignupService(users, terms, provisioning); }

    @Test
    void incompleteTermsDoNotCreateAccountOrCompleteSignup() {
        when(users.getActiveUser(12)).thenReturn(new UserSnapshot(12, "가입 중", null, false));
        when(terms.getRequiredTerms(12)).thenReturn(new RequiredTermsResponse(false, true, true, true));

        assertThatThrownBy(() -> service.complete(12))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(SignupErrorCode.TERMS_NOT_AGREED));
        verify(provisioning, never()).ensureProvisioned(12);
        verify(users, never()).completeSignup(12);
    }

    @Test
    void escrowFailureKeepsSignupPending() {
        when(users.getActiveUser(12)).thenReturn(new UserSnapshot(12, "가입 중", null, false));
        when(terms.getRequiredTerms(12)).thenReturn(new RequiredTermsResponse(true, true, true, true));
        when(provisioning.ensureProvisioned(12))
                .thenThrow(new BusinessException(AccountErrorCode.ESCROW_UNAVAILABLE));

        assertThatThrownBy(() -> service.complete(12)).isInstanceOf(BusinessException.class);
        verify(users, never()).completeSignup(12);
    }

    @Test
    void successfulProvisionCompletesSignupAndRetryIsIdempotent() {
        when(users.getActiveUser(12)).thenReturn(new UserSnapshot(12, "가입 중", null, false));
        when(users.isSignupComplete(12)).thenReturn(false, true);
        when(terms.getRequiredTerms(12)).thenReturn(new RequiredTermsResponse(true, true, true, true));
        when(provisioning.ensureProvisioned(12)).thenReturn("user-key");

        service.complete(12);
        service.complete(12);
        verify(users).completeSignup(12);
        verify(provisioning, times(1)).ensureProvisioned(12);
    }
}
