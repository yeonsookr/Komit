package com.a501.service.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KakaoUserResponseParsingTest {
    /** 카카오 로그인 REST API 문서의 사용자 정보 응답 예시. properties 는 Deprecated 라 뺐습니다. */
    private static final String KAKAO_DOC_SAMPLE = """
            {
              "id": 123456789,
              "connected_at": "2022-04-11T01:45:28Z",
              "kakao_account": {
                "profile_nickname_needs_agreement": false,
                "profile_image_needs_agreement": false,
                "profile": {
                  "nickname": "홍길동",
                  "thumbnail_image_url": "http://yyy.kakao.com/.../img_110x110.jpg",
                  "profile_image_url": "http://yyy.kakao.com/dn/.../img_640x640.jpg",
                  "is_default_image": false
                },
                "has_email": true,
                "email_needs_agreement": false,
                "is_email_valid": true,
                "is_email_verified": true,
                "email": "sample@sample.com"
              }
            }
            """;

    @Test
    void parsesNicknameFromKakaoAccountProfile() {
        JsonMapper mapper = JsonMapper.builder().build();

        KakaoOauthClient.KakaoUserResponse user =
                mapper.readValue(KAKAO_DOC_SAMPLE, KakaoOauthClient.KakaoUserResponse.class);

        assertThat(user.id()).isEqualTo(123456789L);
        assertThat(user.connectedAt()).isNotNull();
        assertThat(user.kakaoAccount()).as("kakao_account 매핑").isNotNull();
        assertThat(user.kakaoAccount().profile()).as("kakao_account.profile 매핑").isNotNull();
        assertThat(user.kakaoAccount().profile().nickname()).isEqualTo("홍길동");
        assertThat(user.kakaoAccount().profile().profileImageUrl()).contains("img_640x640");
        assertThat(user.kakaoAccount().email()).isEqualTo("sample@sample.com");
    }
}
