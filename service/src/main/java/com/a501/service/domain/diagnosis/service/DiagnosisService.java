package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.request.StartDiagnosisRequest;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisProjectResponse;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisResponse;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Survey;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.diagnosis.repository.SurveyRepository;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.service.ProjectService;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisService {
    private static final String UNRESOLVED = "UNRESOLVED";

    private final DiagnosisRepository diagnosisRepository;
    private final InspectionRepository inspectionRepository;
    private final IssueRepository issueRepository;
    private final SurveyRepository surveyRepository;
    private final ProjectService projectService;

    public DiagnosisResponse getDiagnosis(Integer diagnosisId, Integer userId) {
        Diagnosis diagnosis = diagnosisRepository
                .findByIdAndDeletedFalse(diagnosisId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));
        return toResponse(diagnosis, ownedProject(diagnosis.getProjectId(), userId));
    }

    /**
     * 사전점검을 시작한다. 진단은 프로젝트당 한 행이라, 이미 진단한 프로젝트면 회차를 올리고 처음이면 1회차로 연다. 어느 쪽이든 그 회차의 설문지 한 벌이 함께 생긴다.
     */
    @Transactional
    public DiagnosisResponse start(StartDiagnosisRequest request, Integer userId) {
        ProjectSummary project = request.projectId() == null
                ? projectService.create(userId, request.name(), request.description())
                : ownedProject(request.projectId(), userId);

        Diagnosis diagnosis = diagnosisRepository
                .findByProjectIdAndDeletedFalse(project.projectId())
                .map(found -> {
                    found.startNextVersion();
                    return found;
                })
                .orElseGet(() -> diagnosisRepository.save(Diagnosis.of(project.projectId())));

        surveyRepository.save(Survey.of(diagnosis.getId(), diagnosis.getVersion()));
        return toResponse(diagnosis, project);
    }

    /** 남의 프로젝트에 걸린 진단은 존재를 알리지 않는다. 없는 것과 같은 오류로 답한다. (BACKEND_CONVENTIONS 12.1) */
    private ProjectSummary ownedProject(Integer projectId, Integer userId) {
        return projectService
                .findOwned(projectId, userId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));
    }

    private DiagnosisResponse toResponse(Diagnosis diagnosis, ProjectSummary project) {
        Optional<Inspection> inspection =
                inspectionRepository.findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(
                        diagnosis.getId(), diagnosis.getVersion());
        Integer inspectionId = inspection.map(Inspection::getId).orElse(null);

        return new DiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getVersion().intValue(),
                DiagnosisProjectResponse.from(project),
                diagnosis.getUserInput(),
                inspection.map(Inspection::getReadinessScore).orElse(null),
                inspection.map(Inspection::getScannedAt)
                        .map(scannedAt -> scannedAt.atOffset(ZoneOffset.UTC))
                        .orElse(null),
                inspectionId == null
                        ? 0
                        : issueRepository.countByInspectionIdAndDeletedFalseAndStatus(
                                inspectionId, UNRESOLVED),
                inspectionId == null ? 0 : issueRepository.countByInspectionIdAndDeletedFalse(inspectionId),
                issueRepository.countResolvedAt(diagnosis.getId(), diagnosis.getVersion()));
    }
}
