package com.a501.service.domain.diagnosis.repository;

import com.a501.service.domain.diagnosis.entity.Issue;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IssueRepositoryCustom {
    /**
     * 한 검사에서 나온 결함을 거르고 끊어 읽는다. 거르는 조건이 요청마다 달라 쿼리 메서드로는 담기 어렵다.
     *
     * @param status 비우면 모든 처리 상태
     * @param input  비우면 의뢰 내용 관련 여부를 가리지 않음
     */
    Page<Issue> findPage(Integer inspectionId, String status, Boolean input, Pageable pageable);
}
