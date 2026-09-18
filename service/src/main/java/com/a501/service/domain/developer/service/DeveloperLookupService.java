package com.a501.service.domain.developer.service;

import com.a501.service.domain.developer.entity.Developer;
import com.a501.service.domain.developer.repository.DeveloperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeveloperLookupService {
    private final DeveloperRepository developerRepository;

    public Integer findDeveloperId(Integer userId) {
        return developerRepository.findByUserIdAndDeletedFalse(userId)
                .map(Developer::getId)
                .orElse(null);
    }
}
