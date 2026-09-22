package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.project.service.ProjectService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단이 이 사용자 것인지 가린다.
 *
 * <p>진단에는 사용자 식별자가 없어 프로젝트를 거쳐야 하고, 사전점검의 여러 서비스가 같은 확인을 한다. 한곳에 두어 서로를 부르지 않게 한다.
 *
 * <p>없는 진단과 남의 진단을 갈라 답하지 않는다. 어떤 오류로 답할지는 부르는 쪽이 정한다. (BACKEND_CONVENTIONS 12.1)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisOwnership {
    private final DiagnosisRepository diagnosisRepository;
    private final ProjectService projectService;

    public Optional<Diagnosis> findOwned(Integer diagnosisId, Integer userId) {
        return diagnosisRepository
                .findByIdAndDeletedFalse(diagnosisId)
                .filter(diagnosis ->
                        projectService.findOwned(diagnosis.getProjectId(), userId).isPresent());
    }
}
