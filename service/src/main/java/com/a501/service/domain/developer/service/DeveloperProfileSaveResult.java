package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.dto.response.MyDeveloperProfileResponse;

/**
 * 내 프로필 저장 결과. 프로필을 새로 만들었는지에 따라 컨트롤러가 201과 200을 고릅니다.
 *
 * @param created 프로필이 없어 새로 만들었으면 true
 * @param profile 저장된 뒤의 프로필
 */
public record DeveloperProfileSaveResult(boolean created, MyDeveloperProfileResponse profile) {
}
