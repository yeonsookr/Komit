package com.a501.service.domain.contract.service;

import com.a501.service.common.logging.SlowCallThreshold;
import com.a501.service.domain.contract.dto.request.ContractQaSuggestionRequest;
import com.a501.service.domain.diagnosis.client.QaSuggestionClient;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 계약 편집 중인 작업의 확인 문장 후보를 만들며 아무것도 저장하지 않는다. (R-CON-22) */
@Service
@RequiredArgsConstructor
@SlowCallThreshold("30s")
public class ContractQaSuggestionService {
    private final ContractQaSuggestionReader reader;
    private final QaSuggestionClient qaSuggestionClient;

    public QaSuggestionListResponse suggest(
            Integer roomId, Integer userId, ContractQaSuggestionRequest request) {
        String requirement = reader.read(roomId, userId, request);
        return QaSuggestionListResponse.of(qaSuggestionClient.suggest(requirement));
    }
}
