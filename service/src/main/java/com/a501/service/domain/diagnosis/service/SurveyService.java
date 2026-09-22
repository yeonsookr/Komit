package com.a501.service.domain.diagnosis.service;

import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.exception.CommonErrorCode;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.SurveyAnswerType;
import com.a501.service.domain.diagnosis.dto.request.SaveSurveyAnswersRequest.SurveyAnswerRequest;
import com.a501.service.domain.diagnosis.dto.request.ScanResult;
import com.a501.service.domain.diagnosis.dto.response.SurveyOptionResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyQuestionResponse;
import com.a501.service.domain.diagnosis.dto.response.SurveyResponse;
import com.a501.service.domain.diagnosis.entity.Survey;
import com.a501.service.domain.diagnosis.entity.SurveyOption;
import com.a501.service.domain.diagnosis.entity.SurveyQuestion;
import com.a501.service.domain.diagnosis.repository.SurveyOptionRepository;
import com.a501.service.domain.diagnosis.repository.SurveyQuestionRepository;
import com.a501.service.domain.diagnosis.repository.SurveyRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단 회차에 딸린 설문지를 열고 읽고 채운다. 소유권은 이 서비스를 부르는 {@link DiagnosisService} 가 먼저 가린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {
    /** 검사 결과에서 만든 문항 수 상한. (R-SCAN-16) */
    private static final int MAX_SCAN_QUESTIONS = 5;

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final SurveyOptionRepository surveyOptionRepository;

    /** 회차를 열 때 설문지 한 벌과 설문1~3 문항을 함께 심는다. */
    @Transactional
    public Survey open(Integer diagnosisId, Short diagnosisVersion) {
        Survey survey = surveyRepository.save(Survey.of(diagnosisId, diagnosisVersion));
        for (FixedSurveyQuestions.Definition definition : FixedSurveyQuestions.ALL) {
            SurveyQuestion question = surveyQuestionRepository.save(
                    SurveyQuestion.of(survey.getId(), definition.content(), definition.answerType()));
            definition.options().stream()
                    .map(content -> SurveyOption.of(question.getId(), content))
                    .forEach(surveyOptionRepository::save);
        }
        return survey;
    }

    /**
     * 마지막 설문 문항을 검사 결과에서 나온 것으로 갈아 끼운다. 고정 문항 뒤에 이어 붙이면 다시 검사했을 때 지난 문항과 겹쳐 같은 것을 두 번 묻는다. (R-SCAN-14)
     *
     * @param questions 검사 결과의 문항. 다섯 개까지만 쓴다 (R-SCAN-16)
     */
    @Transactional
    public void replaceScanQuestions(
            Integer diagnosisId, Short diagnosisVersion, List<ScanResult.Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        Survey survey = load(diagnosisId, diagnosisVersion);
        List<SurveyQuestion> existing = questionsOf(survey);
        existing.stream()
                .skip(FixedSurveyQuestions.ALL.size())
                .forEach(question -> surveyQuestionRepository.delete(question));

        questions.stream().limit(MAX_SCAN_QUESTIONS).forEach(raw -> {
            SurveyQuestion question = surveyQuestionRepository.save(SurveyQuestion.of(
                    survey.getId(),
                    raw.content(),
                    raw.answerType() == null ? SurveyAnswerType.TEXT : raw.answerType()));
            if (raw.options() != null) {
                raw.options().stream()
                        .map(content -> SurveyOption.of(question.getId(), content))
                        .forEach(surveyOptionRepository::save);
            }
        });
    }

    public SurveyResponse getSurvey(Integer diagnosisId, Short diagnosisVersion) {
        return toResponse(load(diagnosisId, diagnosisVersion));
    }

    /**
     * 보낸 문항의 답만 갈아 끼운다. 보내지 않은 문항은 그대로 남으므로 같은 단계를 다시 보내도 결과가 같다.
     *
     * @return 저장 뒤의 설문지 전체. 화면이 응답으로 상태를 맞춘다
     */
    @Transactional
    public SurveyResponse saveAnswers(
            Integer diagnosisId, Short diagnosisVersion, List<SurveyAnswerRequest> answers) {
        Survey survey = load(diagnosisId, diagnosisVersion);
        Map<Integer, SurveyQuestion> questions = questionsOf(survey).stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));

        for (SurveyAnswerRequest answer : answers) {
            SurveyQuestion question = questions.get(answer.questionId());
            if (question == null) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            }
            if (question.answerType().isText()) {
                writeText(question, answer);
            } else {
                choose(question, answer);
            }
        }
        return toResponse(survey);
    }

    /**
     * 설문을 끝낸 것으로 바꾼다. 답이 다 찼는지로 판정하지 않으면 설문4가 생기기 전에도 완료로 찍히므로, 문항이 모두 있고 모두 답이 있는지 여기서 확인한다.
     */
    @Transactional
    public SurveyResponse complete(Integer diagnosisId, Short diagnosisVersion) {
        Survey survey = load(diagnosisId, diagnosisVersion);
        if (survey.isDone()) {
            throw new BusinessException(DiagnosisErrorCode.SURVEY_NOT_COMPLETABLE);
        }

        List<SurveyQuestion> questions = questionsOf(survey);
        Map<Integer, List<SurveyOption>> optionsByQuestion = optionsOf(questions);
        boolean scanQuestionsArrived = questions.size() > FixedSurveyQuestions.ALL.size();
        boolean everyQuestionAnswered = questions.stream()
                .allMatch(question -> optionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                        .anyMatch(SurveyOption::isSelected));
        if (!scanQuestionsArrived || !everyQuestionAnswered) {
            throw new BusinessException(DiagnosisErrorCode.SURVEY_NOT_COMPLETABLE);
        }

        survey.complete();
        return toResponse(survey);
    }

    /** 사용자가 주관식에 적은 증상. 진단서 머리를 채우려고 진단에도 옮겨 적는다. */
    public String findUserInput(Integer diagnosisId, Short diagnosisVersion) {
        List<SurveyQuestion> questions = questionsOf(load(diagnosisId, diagnosisVersion));
        if (questions.size() <= FixedSurveyQuestions.USER_INPUT_INDEX) {
            return null;
        }
        return optionsOf(questions)
                .getOrDefault(questions.get(FixedSurveyQuestions.USER_INPUT_INDEX).getId(), List.of())
                .stream()
                .filter(SurveyOption::isSelected)
                .map(SurveyOption::getContent)
                .findFirst()
                .orElse(null);
    }

    private void writeText(SurveyQuestion question, SurveyAnswerRequest answer) {
        if (!answer.selectedOptionIds().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        List<SurveyOption> written = optionsOf(List.of(question))
                .getOrDefault(question.getId(), List.of());
        if (answer.text() == null || answer.text().isBlank()) {
            written.forEach(SurveyOption::deselect);
            return;
        }
        if (written.isEmpty()) {
            surveyOptionRepository.save(
                    SurveyOption.ofWrittenAnswer(question.getId(), answer.text()));
            return;
        }
        written.getFirst().rewrite(answer.text());
    }

    private void choose(SurveyQuestion question, SurveyAnswerRequest answer) {
        if (answer.text() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (question.answerType() == SurveyAnswerType.SINGLE
                && answer.selectedOptionIds().size() > 1) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        List<SurveyOption> options = optionsOf(List.of(question))
                .getOrDefault(question.getId(), List.of());
        List<Integer> known = options.stream().map(SurveyOption::getId).toList();
        if (!known.containsAll(answer.selectedOptionIds())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        for (SurveyOption option : options) {
            if (answer.selectedOptionIds().contains(option.getId())) {
                option.select();
            } else {
                option.deselect();
            }
        }
    }

    private Survey load(Integer diagnosisId, Short diagnosisVersion) {
        return surveyRepository
                .findByDiagnosisIdAndDiagnosisVersionAndDeletedFalse(diagnosisId, diagnosisVersion)
                .orElseThrow(() -> new BusinessException(DiagnosisErrorCode.DIAGNOSIS_NOT_FOUND));
    }

    private List<SurveyQuestion> questionsOf(Survey survey) {
        return surveyQuestionRepository.findBySurveyIdAndDeletedFalseOrderByIdAsc(survey.getId());
    }

    private Map<Integer, List<SurveyOption>> optionsOf(List<SurveyQuestion> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        return surveyOptionRepository
                .findByQuestionIdInAndDeletedFalseOrderByIdAsc(
                        questions.stream().map(SurveyQuestion::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(SurveyOption::getQuestionId));
    }

    private SurveyResponse toResponse(Survey survey) {
        List<SurveyQuestion> questions = questionsOf(survey);
        Map<Integer, List<SurveyOption>> optionsByQuestion = optionsOf(questions);
        return SurveyResponse.of(
                survey,
                questions.stream()
                        .map(question -> SurveyQuestionResponse.of(
                                question,
                                optionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                                        .map(SurveyOptionResponse::from)
                                        .toList()))
                        .toList());
    }
}
