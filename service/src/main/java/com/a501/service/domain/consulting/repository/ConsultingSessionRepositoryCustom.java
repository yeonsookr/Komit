package com.a501.service.domain.consulting.repository;

import java.util.Optional;

public interface ConsultingSessionRepositoryCustom {
    /**
     * 다음에 전사할 세션. 전사를 기다리는 세션 중 가장 먼저 끝난 것입니다.
     *
     * <p>끝났고, 참가자 두 사람의 녹음이 모두 붙었고, 전사도 전사 불가 판정도 없는 세션입니다.
     * (`ai/worker/docs/job-contract.md` 11.3)
     */
    Optional<Integer> findNextToTranscribe();
}
