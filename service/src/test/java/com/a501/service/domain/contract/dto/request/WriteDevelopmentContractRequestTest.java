package com.a501.service.domain.contract.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

/**
 * 개발 계약서 작성 요청이 요청 안에서 가릴 수 있는 것을 거르는지 본다. 목 명세의 같은 검증과 문구가 같아야 한다.
 *
 * <p>문구는 실제 서버와 같은 {@code messages.properties} 로 풀어 비교한다. 키를 잘못 적으면 문구 대신 키가 그대로
 * 나가는데, 제약 이름만 보면 그것을 놓친다.
 */
class WriteDevelopmentContractRequestTest {
    private static LocalValidatorFactoryBean validator;

    @BeforeAll
    static void setUpValidator() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages);
        validator.afterPropertiesSet();
    }

    @AfterAll
    static void closeValidator() {
        validator.close();
    }

    @Test
    @DisplayName("JSON 으로 온 요청을 읽는다. 작업마다 기존 요구사항과 새 작업이 섞여 온다")
    void readsJson() {
        String json = """
                {"startDate":"2026-08-26","dueDate":"2026-09-16","freeRepairDays":14,
                 "milestones":[
                   {"issueId":312,"title":"관리자 인증 강화","price":400000,"workDate":10,"qaCriteria":["로그인 화면이 뜬다"]},
                   {"title":"관리자 화면에 검색 추가","detail":"회원을 찾을 수 있다","price":0,"qaCriteria":["검색이 된다"]}],
                 "deliverableTitles":["소스 파일 전체"],"message":"잘 부탁드립니다."}""";

        WriteDevelopmentContractRequest request =
                JsonMapper.builder().build().readValue(json, WriteDevelopmentContractRequest.class);

        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(request.milestones()).extracting(WriteMilestoneRequest::issueId).containsExactly(312, null);
        assertThat(request.milestones().get(0).title()).isEqualTo("관리자 인증 강화");
        assertThat(request.milestones().get(1).detail()).isEqualTo("회원을 찾을 수 있다");
        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("선택 항목은 비워도 된다")
    void acceptsOmittedOptionalFields() {
        WriteDevelopmentContractRequest request = new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 0,
                List.of(new WriteMilestoneRequest(312, null, null, 0, null, List.of("로그인 화면이 뜬다"))),
                null, null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("필수 항목이 없으면 거부한다")
    void rejectsMissingRequiredFields() {
        assertThat(violations(new WriteDevelopmentContractRequest(null, null, null, null, null, null)))
                .containsExactlyInAnyOrder(
                        "startDate=필수 항목입니다.",
                        "dueDate=필수 항목입니다.",
                        "freeRepairDays=필수 항목입니다.",
                        "milestones=필수 항목입니다.");
    }

    @Test
    @DisplayName("작업이 하나도 없으면 거부한다")
    void rejectsEmptyMilestones() {
        assertThat(violations(request(List.of()))).containsExactly("milestones=1개 이상이어야 합니다.");
    }

    @Test
    @DisplayName("새 작업에 이름이 없으면 거부한다")
    void rejectsNewWorkWithoutTitle() {
        assertThat(violations(request(List.of(
                        new WriteMilestoneRequest(null, null, "설명만 있다", 1000, null, List.of("검색이 된다"))))))
                .containsExactly("milestones[0].title=필수 항목입니다.");
    }

    @Test
    @DisplayName("이름·설명은 빈 문자열일 수 없다. 공백만 있는 이름은 목처럼 받는다")
    void rejectsEmptyTitleButNotBlankOne() {
        assertThat(violations(request(List.of(
                        new WriteMilestoneRequest(312, "", null, 1000, null, List.of("로그인 화면이 뜬다")),
                        new WriteMilestoneRequest(null, "", null, 1000, null, List.of("검색이 된다")),
                        new WriteMilestoneRequest(null, "   ", null, 1000, null, List.of("검색이 된다")),
                        new WriteMilestoneRequest(313, null, "", 1000, null, List.of("로그인 화면이 뜬다"))))))
                .containsExactlyInAnyOrder(
                        "milestones[0].title=1자 이상이어야 합니다.",
                        "milestones[1].title=1자 이상이어야 합니다.",
                        "milestones[3].detail=1자 이상이어야 합니다.");
    }

    @Test
    @DisplayName("기존 요구사항 작업은 계약서에서 바꾼 이름과 설명을 함께 보낼 수 있다")
    void acceptsRenamedExistingIssue() {
        assertThat(violations(request(List.of(
                        new WriteMilestoneRequest(312, "관리자 인증 강화", "토큰도 확인한다", 1000, null, List.of("로그인 화면이 뜬다"))))))
                .isEmpty();
    }

    @Test
    @DisplayName("같은 요구사항을 두 작업에 담으면 거부한다")
    void rejectsDuplicateIssue() {
        assertThat(violations(request(List.of(existing(312), existing(313), existing(312)))))
                .containsExactly("milestones=같은 요구사항을 두 작업에 담을 수 없습니다.");
    }

    @Test
    @DisplayName("새 작업은 여럿이어도 겹치지 않는다")
    void acceptsSeveralNewWorks() {
        assertThat(violations(request(List.of(newWork(), newWork())))).isEmpty();
    }

    @Test
    @DisplayName("하자보수 기간·금액·작업 기간이 음수면 거부한다")
    void rejectsNegativeNumbers() {
        WriteDevelopmentContractRequest request = new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), -1,
                List.of(new WriteMilestoneRequest(312, null, null, -1, -1, List.of("로그인 화면이 뜬다"))),
                null, null);

        assertThat(violations(request)).containsExactlyInAnyOrder(
                "freeRepairDays=0 이상이어야 합니다.",
                "milestones[0].price=0 이상이어야 합니다.",
                "milestones[0].workDate=0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("작업 금액이 없으면 거부한다")
    void rejectsMissingPrice() {
        assertThat(violations(request(List.of(
                        new WriteMilestoneRequest(312, null, null, null, null, List.of("로그인 화면이 뜬다"))))))
                .containsExactly("milestones[0].price=필수 항목입니다.");
    }

    @Test
    @DisplayName("확인 문장이 없거나 비었거나 500자를 넘으면 거부한다")
    void rejectsBadQaCriteria() {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);

        assertThat(violations(request(List.of(
                        new WriteMilestoneRequest(311, null, null, 1000, null, null),
                        new WriteMilestoneRequest(312, null, null, 1000, null, List.of()),
                        new WriteMilestoneRequest(313, null, null, 1000, null, List.of("")),
                        new WriteMilestoneRequest(314, null, null, 1000, null, List.of("가".repeat(501))),
                        new WriteMilestoneRequest(315, null, null, 1000, null, withNull)))))
                .containsExactlyInAnyOrder(
                        "milestones[0].qaCriteria=필수 항목입니다.",
                        "milestones[1].qaCriteria=1개 이상이어야 합니다.",
                        "milestones[2].qaCriteria[0].<list element>=1자 이상이어야 합니다.",
                        "milestones[3].qaCriteria[0].<list element>=500자 이하여야 합니다.",
                        "milestones[4].qaCriteria[0].<list element>=필수 항목입니다.");
    }

    @Test
    @DisplayName("글자 수 한도를 넘으면 거부한다")
    void rejectsTooLongText() {
        WriteDevelopmentContractRequest request = new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14,
                List.of(new WriteMilestoneRequest(
                        null, "가".repeat(201), "가".repeat(1001), 1000, null, List.of("검색이 된다"))),
                List.of("가".repeat(101), ""),
                "가".repeat(301));

        assertThat(violations(request)).containsExactlyInAnyOrder(
                "milestones[0].title=200자 이하여야 합니다.",
                "milestones[0].detail=1000자 이하여야 합니다.",
                "deliverableTitles[0].<list element>=100자 이하여야 합니다.",
                "deliverableTitles[1].<list element>=1자 이상이어야 합니다.",
                "message=300자 이하여야 합니다.");
    }

    @Test
    @DisplayName("한도와 같은 길이는 받는다")
    void acceptsTextAtTheLimit() {
        WriteDevelopmentContractRequest request = new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26), LocalDate.of(2026, 9, 16), 14,
                List.of(new WriteMilestoneRequest(
                        null, "가".repeat(200), "가".repeat(1000), 1000, null, List.of("가".repeat(500)))),
                List.of("가".repeat(100)),
                "가".repeat(300));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    @DisplayName("작업 자리에 null 이 오면 거부한다")
    void rejectsNullMilestone() {
        assertThat(violations(request(Arrays.asList(existing(312), null))))
                .containsExactly("milestones[1].<list element>=필수 항목입니다.");
    }

    @Test
    @DisplayName("납기일이 착수일보다 이르면 알린다. 같은 날은 받는다")
    void detectsDueDateBeforeStart() {
        assertThat(dated(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15)).dueDateBeforeStart()).isTrue();
        assertThat(dated(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 16)).dueDateBeforeStart()).isFalse();
        assertThat(dated(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 17)).dueDateBeforeStart()).isFalse();
        // 날짜가 빠진 것은 필수 항목 검증이 422 로 거른다. 여기서 400 을 겹쳐 내지 않는다.
        assertThat(dated(null, LocalDate.of(2026, 9, 15)).dueDateBeforeStart()).isFalse();
        assertThat(dated(LocalDate.of(2026, 9, 16), null).dueDateBeforeStart()).isFalse();
    }

    private static WriteDevelopmentContractRequest request(List<WriteMilestoneRequest> milestones) {
        return new WriteDevelopmentContractRequest(
                LocalDate.of(2026, 8, 26),
                LocalDate.of(2026, 9, 16),
                14,
                milestones,
                List.of("소스 파일 전체"),
                "잘 부탁드립니다.");
    }

    private static WriteDevelopmentContractRequest dated(LocalDate startDate, LocalDate dueDate) {
        return new WriteDevelopmentContractRequest(startDate, dueDate, 14, List.of(existing(312)), null, null);
    }

    private static WriteMilestoneRequest existing(int issueId) {
        return new WriteMilestoneRequest(issueId, null, null, 400000, 10, List.of("로그인 화면이 뜬다"));
    }

    private static WriteMilestoneRequest newWork() {
        return new WriteMilestoneRequest(null, "관리자 화면에 검색 추가", null, 200000, null, List.of("검색이 된다"));
    }

    private static Set<String> violations(WriteDevelopmentContractRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + "=" + violation.getMessage())
                .collect(Collectors.toSet());
    }
}
