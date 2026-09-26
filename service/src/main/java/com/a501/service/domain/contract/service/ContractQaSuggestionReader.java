package com.a501.service.domain.contract.service;

import com.a501.service.domain.contract.dto.request.ContractQaSuggestionRequest;
import com.a501.service.domain.contract.entity.Contract;
import com.a501.service.domain.contract.entity.DevelopmentContract;
import com.a501.service.domain.contract.entity.Milestone;
import com.a501.service.domain.contract.repository.ContractRepository;
import com.a501.service.domain.contract.repository.DevelopmentContractRepository;
import com.a501.service.domain.contract.repository.MilestoneRepository;
import com.a501.service.domain.room.dto.ContractRoomView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 계약 편집 권한과 작업 범위를 확인하고 AI에 넘길 현재 작업 문구를 만든다. */
@Component
@RequiredArgsConstructor
public class ContractQaSuggestionReader {
    private final DevelopmentContractEditingPolicy editingPolicy;
    private final ContractRepository contractRepository;
    private final DevelopmentContractRepository developmentContractRepository;
    private final MilestoneRepository milestoneRepository;

    @Transactional(readOnly = true)
    public String read(Integer roomId, Integer userId, ContractQaSuggestionRequest request) {
        ContractRoomView room = editingPolicy.requireDeveloperRoom(roomId, userId);
        List<Milestone> previous = List.of();
        Contract contract = contractRepository.findByRoomIdAndDeletedFalse(roomId).orElse(null);
        if (contract != null) {
            editingPolicy.requireWritable(contract);
            previous = developmentContractRepository
                    .findByContractIdAndDeletedFalse(contract.getId())
                    .map(DevelopmentContract::getId)
                    .map(milestoneRepository::findAllByDevelopmentContractIdAndDeletedFalseOrderByIdAsc)
                    .orElseGet(List::of);
        }
        if (request.issueId() != null) {
            editingPolicy.requireKnownIssues(room.projectId(), previous, List.of(request.issueId()));
        }
        return request.detail() == null || request.detail().isBlank()
                ? request.title()
                : request.title() + System.lineSeparator() + request.detail();
    }
}
