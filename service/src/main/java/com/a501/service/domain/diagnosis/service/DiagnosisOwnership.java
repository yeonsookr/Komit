package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.project.service.ProjectService;
import com.a501.service.domain.room.service.RoomQueryService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단을 누가 만질 수 있는지 가린다.
 *
 * <p>진단에는 사용자 식별자가 없어 프로젝트를 거쳐야 하고, 사전점검의 여러 서비스가 같은 확인을 한다. 한곳에 두어 서로를 부르지 않게 한다.
 *
 * <p><b>고치는 것과 읽는 것을 나눈다.</b> 요구사항을 더하거나 해결로 내리거나 다시 검사하는 일은 의뢰인만
 * 할 수 있지만, 상담 요청을 받은 개발자는 무엇을 고쳐야 하는지 읽을 수 있어야 한다. (`R-REQ-05`)
 *
 * <p>없는 진단과 남의 진단을 갈라 답하지 않는다. 어떤 오류로 답할지는 부르는 쪽이 정한다. (BACKEND_CONVENTIONS 12.1)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisOwnership {
    private final DiagnosisRepository diagnosisRepository;
    private final ProjectService projectService;
    private final RoomQueryService roomQueryService;

    /** 이 진단을 고칠 수 있는 사람. 프로젝트를 맡긴 의뢰인뿐이다. */
    public Optional<Diagnosis> findOwned(Integer diagnosisId, Integer userId) {
        return diagnosisRepository
                .findByIdAndDeletedFalse(diagnosisId)
                .filter(diagnosis ->
                        projectService.findOwned(diagnosis.getProjectId(), userId).isPresent());
    }

    /**
     * 이 진단을 읽을 수 있는 사람. 의뢰인과, 그 프로젝트에 방이 열린 개발자다.
     *
     * <p>개발자가 보는 범위는 부르는 쪽이 좁힌다. 여기서는 들여보낼지만 정한다.
     */
    public Optional<Diagnosis> findReadable(Integer diagnosisId, Integer userId) {
        return diagnosisRepository
                .findByIdAndDeletedFalse(diagnosisId)
                .filter(diagnosis -> isClient(diagnosis, userId) || isInvitedDeveloper(diagnosis, userId));
    }

    /** 이 사람이 진단을 만들지는 못하고 읽기만 하는 개발자인지. 보여줄 범위를 좁힐 때 쓴다. */
    public boolean isReaderOnly(Diagnosis diagnosis, Integer userId) {
        return !isClient(diagnosis, userId);
    }

    private boolean isClient(Diagnosis diagnosis, Integer userId) {
        return projectService.findOwned(diagnosis.getProjectId(), userId).isPresent();
    }

    private boolean isInvitedDeveloper(Diagnosis diagnosis, Integer userId) {
        return roomQueryService.hasRoomAsDeveloper(diagnosis.getProjectId(), userId);
    }
}
