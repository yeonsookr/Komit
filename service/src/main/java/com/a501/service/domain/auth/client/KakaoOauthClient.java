package com.a501.service.domain.auth.client;

import java.time.Instant;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.config.KakaoProperties;
import com.a501.service.domain.auth.AuthErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoOauthClient {
    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoOauthClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public KakaoAuthorization authorize(String code, String redirectUri) {
        try {
            KakaoTokenResponse token = exchangeCode(code, redirectUri);
            KakaoUserResponse user = fetchUser(token.accessToken());
            String nickname = nickname(user);
            if (token.refreshToken() == null || token.refreshToken().isBlank()
                    || user.id() == null || nickname == null || nickname.isBlank()) {
                throw new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED);
            }
            KakaoAccount account = user.kakaoAccount();
            return new KakaoAuthorization(
                    user.id(),
                    nickname,
                    profileImageUrl(user),
                    account == null ? null : account.email(),
                    account != null && Boolean.TRUE.equals(account.isEmailVerified()),
                    account == null ? null : account.isEmailValid(),
                    user.connectedAt(),
                    token.refreshToken(),
                    token.refreshTokenExpiresIn());
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpClientErrorException exception) {
            throw new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED);
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE);
        }
    }

    private KakaoTokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        KakaoTokenResponse response = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED);
        }
        return response;
    }

    private KakaoUserResponse fetchUser(String kakaoAccessToken) {
        KakaoUserResponse response = restClient.get()
                .uri(properties.userInfoUri())
                .headers(headers -> headers.setBearerAuth(kakaoAccessToken))
                .retrieve()
                .body(KakaoUserResponse.class);
        if (response == null) {
            throw new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED);
        }
        return response;
    }

    private String nickname(KakaoUserResponse user) {
        if (user.kakaoAccount() != null && user.kakaoAccount().profile() != null
                && user.kakaoAccount().profile().nickname() != null) {
            return user.kakaoAccount().profile().nickname();
        }
        return user.properties() == null ? null : user.properties().nickname();
    }

    private String profileImageUrl(KakaoUserResponse user) {
        if (user.kakaoAccount() != null && user.kakaoAccount().profile() != null
                && user.kakaoAccount().profile().profileImageUrl() != null) {
            return user.kakaoAccount().profile().profileImageUrl();
        }
        return user.properties() == null ? null : user.properties().profileImage();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("refresh_token_expires_in") long refreshTokenExpiresIn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoUserResponse(
            Long id,
            @JsonProperty("connected_at") Instant connectedAt,
            Properties properties,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(String nickname, @JsonProperty("profile_image") String profileImage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(
            Profile profile,
            String email,
            @JsonProperty("is_email_verified") Boolean isEmailVerified,
            @JsonProperty("is_email_valid") Boolean isEmailValid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {
    }
}
