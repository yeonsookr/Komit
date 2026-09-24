package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.dto.request.SaveSurveyAnswersRequest.SurveyAnswerRequest;
import com.a501.service.domain.diagnosis.dto.request.StartDiagnosisRequest;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisProjectResponse;
import com.a501.service.domain.diagnosis.dto.request.CreateIssueRequest;
import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import com.a501.service.domain.diagnosis.dto.request.WriteRequirementsRequest.WriteRequirement;
import com.a501.service.domain.diagnosis.dto.response.DiagnosisResponse;
import com.a501.service.domain.diagnosis.dto.response.InspectionResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueListResponse;
import com.a501.service.domain.diagnosis.dto.response.IssueResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyResponse;
import com.a501.service.domain.diagnosis.dto.response.UnfinishedSurveyListResponse;
import com.a501.service.domain.diagnosis.dto.response.UnfinishedSurveyListResponse.UnfinishedSurveyResponse;
import com.a501.service.domain.diagnosis.entity.Diagnosis;
import com.a501.service.domain.diagnosis.entity.Inspection;
import com.a501.service.domain.diagnosis.entity.Survey;
import com.a501.service.domain.diagnosis.repository.DiagnosisRepository;
import com.a501.service.domain.diagnosis.repository.InspectionRepository;
import com.a501.service.domain.diagnosis.repository.IssueRepository;
import com.a501.service.domain.project.dto.ProjectSummary;
import com.a501.service.domain.project.service.ProjectService;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosisService {
    private static final String UNRESOLVED = "UNRESOLVED";

    private final DiagnosisRepository diagnosisRepository;
    private final InspectionRepository inspectionRepository;
    private final IssueRepository issueRepository;
    private final SurveyService surveyService;
    private final ProjectService projectService;
    private final ScanFileWriter scanFileWriter;
    private final ScanResultReader scanResultReader;
    private final InspectionService inspectionService;
    private final IssueQueryService issueQueryService;
    private final RequirementService requirementService;
    private final DiagnosisOwnership diagnosisOwnership;

    /**
     * 설문을 끝내지 않고 나간 진단. 다시 들어온 사람이 처음부터 하지 않고 이어서 하게 한다.
     *
     * <p>끝냈는지는 설문의 완료 여부로 본다. 검사를 돌렸는지와는 무관해서, 설문3까지 하고 나간 사람도 남는다.
     */
    public UnfinishedSurveyListResponse getUnfinishedSurveys(Integer userId) {
        Map<Integer, String> projectNames = projectService.findAllOwned(userId).stream()
                .collect(Collectors.toMap(ProjectSummary::projectId, ProjectSummary::name));
        if (projectNames.isEmpty()) {
            return new UnfinishedSurveyListResponse(List.of());
        }

        List<Diagnosis> diagnoses =
                diagnosisRepository.findByProjectIdInAndDeletedFalse(projectNames.keySet());
        // 지금 회차의 설문만 본다. 지난 회차의 설문은 재진단으로 넘어간 것이라 이어서 할 것이 아니다.
        Set<String> unfinished = surveyService
                .findUnfinished(diagnoses.stream().map(Diagnosis::getId).toList())
                .stream()
                .map(survey -> survey.getDiagnosisId() + ":" + survey.getDiagnosisVersion())
                .collect(Collectors.toSet());

        return new UnfinishedSurveyListResponse(diagnoses.stream()
                .filter(diagnosis ->
                        unfinished.contains(diagnosis.getId() + ":" + diagnosis.getVersion()))
                .sorted(Comparator.comparing(Diagnosis::getUpdatedAt).reversed())
                .map(diagnosis -> new UnfinishedSurveyResponse(
                        diagnosis.getId(),
                        projectNames.get(diagnosis.getProjectId()),
                        diagnosis.getUpdatedAt().atOffset(ZoneOffset.UTC)))
                .toList());
    }

    /**
     * 진단 한 건.
     *
     * <p><b>상담 요청을 받은 개발자에게는 좁혀서 내보낸다.</b> 점수와 결함 수는 요청서에 담기지 않은
     * 것까지 드러내므로 비운다. 의뢰인이 골라 보낸 것 밖은 보여주지 않기로 한 것이다. (`R-REQ-03`)
     */
    public DiagnosisResponse getDiagnosis(Integer diagnosisId, Integer userId) {
        Diagnosis diagnosis = readableDiagnosis(diagnosisId, userId);
        if (diagnosisOwnership.isReaderOnly(diagnosis, userId)) {
            return toRequestResponse(diagnosis, project(diagnosis.getProjectId()));
        }
        return toResponse(diagnosis, ownedProject(diagnosis.getProjectId(), userId));
    }

    /** 의뢰인이 자기 AI 도구에서 돌릴 검사 파일. 이 진단의 번호와 적은 증상이 담긴다. */
    public byte[] getScanFile(Integer diagnosisId, Integer userId) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        ProjectSummary project = ownedProject(diagnosis.getProjectId(), userId);
        return scanFileWriter.write(diagnosis.getId(), project.name(), diagnosis.getUserInput());
    }

    /** 의뢰인이 자기 AI 도구로 돌린 검사 결과를 받는다. 코드는 올라오지 않고 결과 파일만 온다. */
    @Transactional
    public InspectionResponse uploadScanResult(
            Integer diagnosisId, Integer userId, MultipartFile file) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        ScanResult result = scanResultReader.read(file, diagnosis.getId());
        return inspectionService.record(
                diagnosis.getId(), diagnosis.getProjectId(), diagnosis.getVersion(), result);
    }

    /**
     * 이 진단 회차의 결함. 요청서도 이 목록으로 그린다.
     *
     * <p><b>상담 요청을 받은 개발자에게는 요청서에 담긴 것만 내보낸다.</b> 고르지 않은 것은 의뢰인이
     * 보여주지 않기로 한 것이다. (`R-REQ-03`)
     */
    public IssueListResponse getIssues(
            Integer diagnosisId, Integer userId, String status, Boolean input, int page, int size) {
        Diagnosis diagnosis = readableDiagnosis(diagnosisId, userId);
        return issueQueryService.getIssues(
                diagnosis.getId(),
                diagnosis.getVersion(),
                status,
                input,
                diagnosisOwnership.isReaderOnly(diagnosis, userId),
                page,
                size);
    }

    /** 검사가 찾지 못했지만 고치고 싶은 것을 직접 적어 넣는다. */
    @Transactional
    public IssueResponse addIssue(
            Integer diagnosisId, Integer userId, CreateIssueRequest request) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        return requirementService.addIssue(diagnosis.getId(), diagnosis.getVersion(), request);
    }

    /** 요청서를 통째로 받아 보낸 내용으로 맞춘다. */
    @Transactional
    public IssueListResponse writeRequirements(
            Integer diagnosisId, Integer userId, List<WriteRequirement> requirements) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        return requirementService.write(diagnosis.getId(), diagnosis.getVersion(), requirements);
    }

    public SurveyResponse getSurvey(Integer diagnosisId, Integer userId) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        return surveyService.getSurvey(diagnosis.getId(), diagnosis.getVersion());
    }

    /** 답을 저장하고, 주관식에 적은 증상은 진단서 머리에 실리도록 진단에도 옮겨 적는다. */
    @Transactional
    public SurveyResponse saveSurveyAnswers(
            Integer diagnosisId, Integer userId, List<SurveyAnswerRequest> answers) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        SurveyResponse saved =
                surveyService.saveAnswers(diagnosis.getId(), diagnosis.getVersion(), answers);
        if (answers.stream().anyMatch(answer -> answer.text() != null)) {
            diagnosis.writeUserInput(
                    surveyService.findUserInput(diagnosis.getId(), diagnosis.getVersion()));
        }
        return saved;
    }

    @Transactional
    public SurveyResponse completeSurvey(Integer diagnosisId, Integer userId) {
        Diagnosis diagnosis = ownedDiagnosis(diagnosisId, userId);
        return surveyService.complete(diagnosis.getId(), diagnosis.getVersion());
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

        surveyService.open(diagnosis.getId(), diagnosis.getVersion());
        return toResponse(diagnosis, project);
    }

    /** 진단이 없는 것과 남의 것을 갈라 답하지 않는다. 소유권은 진단에 없고 프로젝트에 있다. */
    private Diagnosis ownedDiagnosis(Integer diagnosisId, Integer userId) {
        Diagnosis diagnosis = diagnosisRepository
                .findByIdAndDeletedFalse(diagnosisId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));
        ownedProject(diagnosis.getProjectId(), userId);
        return diagnosis;
    }

    /** 남의 프로젝트에 걸린 진단은 존재를 알리지 않는다. 없는 것과 같은 오류로 답한다. (BACKEND_CONVENTIONS 12.1) */
    /** 읽을 수 있는 사람인지만 가린다. 의뢰인과, 그 프로젝트에 방이 열린 개발자다. */
    private Diagnosis readableDiagnosis(Integer diagnosisId, Integer userId) {
        return diagnosisOwnership
                .findReadable(diagnosisId, userId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));
    }

    /** 이미 들여보낼지 가린 뒤에 읽는다. 주인이 아닌 사람도 프로젝트 이름은 봐야 한다. */
    private ProjectSummary project(Integer projectId) {
        return projectService
                .find(projectId)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));
    }

    /**
     * 요청을 받은 개발자에게 나가는 모양.
     *
     * <p>점수와 결함 수를 비운다. 숫자만으로도 요청서 밖에 무엇이 얼마나 있는지 드러난다. 설문 답변은
     * 애초에 이 응답에 없다 — 예산과 기한이 거기 적히므로 견적 전에 보이면 안 된다. (`R-SCAN-14`)
     */
    private DiagnosisResponse toRequestResponse(Diagnosis diagnosis, ProjectSummary project) {
        return new DiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getVersion().intValue(),
                DiagnosisProjectResponse.from(project),
                // 의뢰인이 적은 증상이다. 무엇을 고쳐 달라는 것인지라 요청서와 함께 읽어야 한다.
                diagnosis.getUserInput(),
                null,
                null,
                0,
                0,
                0);
    }

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
