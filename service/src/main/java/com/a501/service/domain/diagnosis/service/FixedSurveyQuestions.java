package com.a501.service.domain.diagnosis.service;

import com.a501.service.domain.diagnosis.SurveyAnswerType;
import java.util.List;

/**
 * 설문1~3의 고정 문항. 모든 프로젝트가 같은 문구를 받으므로 진단 회차를 열 때 그대로 심는다. 설문4는 올린 검사 결과에서 나오므로 여기 없다. (R-SCAN-14)
 *
 * <p>화면도 같은 문구를 갖고 있어 설문1~3을 이 API 없이 그린다. 문구를 고치면 화면과 함께 고쳐야 한다.
 */
final class FixedSurveyQuestions {
    static final List<Definition> ALL = List.of(
            new Definition(
                    "지금 어디까지 왔나요",
                    SurveyAnswerType.SINGLE,
                    List.of(
                            "내 컴퓨터에서만 돌아갑니다",
                            "배포는 했는데 아직 아무도 안 씁니다",
                            "사용자가 조금 있습니다",
                            "매출이 발생하고 있습니다")),
            new Definition(
                    "어떤 상태인가요?",
                    SurveyAnswerType.MULTI,
                    List.of(
                            "화면은 되는데 기능이 안 돼요",
                            "배포를 못 하고 있어요",
                            "보안이 걱정돼요",
                            "곧 실제 사용자를 받아요",
                            "결제를 받아야 해요",
                            "기능을 더 못 넣겠어요")),
            new Definition("무슨 일이 있었는지 적어주세요", SurveyAnswerType.TEXT, List.of()));

    /** 사용자가 적은 증상이 진단서 머리의 {@code userInput} 이 되는 문항. 셋 중 유일한 주관식이다. */
    static final int USER_INPUT_INDEX = 2;

    private FixedSurveyQuestions() {
    }

    record Definition(String content, SurveyAnswerType answerType, List<String> options) {}
}
