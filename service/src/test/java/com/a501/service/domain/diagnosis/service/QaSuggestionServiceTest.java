package com.a501.service.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a501.service.common.exception.BusinessException;
import com.a501.service.common.logging.LoggingAspect;
import com.a501.service.common.logging.LoggingProperties;
import com.a501.service.domain.diagnosis.DiagnosisErrorCode;
import com.a501.service.domain.diagnosis.client.QaSuggestionClient;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionListResponse;
import com.a501.service.domain.diagnosis.dto.response.QaSuggestionResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class QaSuggestionServiceTest {
    private static final Integer ISSUE_ID = 312;
    private static final Integer USER_ID = 12;
    private static final String REQUIREMENT = "관리자 화면에 인증이 없다";

    private IssueRequirementReader issueRequirementReader;
    private QaSuggestionClient qaSuggestionClient;
    private QaSuggestionService service;

    @BeforeEach
    void setUp() {
        issueRequirementReader = mock(IssueRequirementReader.class);
        qaSuggestionClient = mock(QaSuggestionClient.class);
        service = new QaSuggestionService(issueRequirementReader, qaSuggestionClient);
    }

    @Test
    @DisplayName("읽어 온 문장을 그대로 AI 에 넘기고 목록으로 감싸 돌려준다")
    void passesRequirementAndWrapsResult() {
        when(issueRequirementReader.read(ISSUE_ID, USER_ID)).thenReturn(REQUIREMENT);
        when(qaSuggestionClient.suggest(any()))
                .thenReturn(List.of(
                        new QaSuggestionResponse("첫째"),
                        new QaSuggestionResponse("둘째"),
                        new QaSuggestionResponse("셋째")));

        QaSuggestionListResponse response = service.suggest(ISSUE_ID, USER_ID);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(qaSuggestionClient).suggest(sent.capture());
        assertThat(sent.getValue()).isEqualTo(REQUIREMENT);
        assertThat(response.content()).hasSize(3);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("느린 호출 경고 기준을 따로 둬서 공용 기준을 넘겨도 경고가 아니다")
    void hasItsOwnSlowCallThreshold() {
        Logger aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
        aspectLogger.setLevel(Level.DEBUG);
        when(issueRequirementReader.read(ISSUE_ID, USER_ID)).thenAnswer(invocation -> {
            Thread.sleep(20);
            return REQUIREMENT;
        });
        when(qaSuggestionClient.suggest(any())).thenReturn(List.of(new QaSuggestionResponse("문장")));

        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        // 공용 기준을 1ms 로 두어도, 이 클래스에 붙은 30s 가 이긴다.
        factory.addAspect(new LoggingAspect(new LoggingProperties(Duration.ofMillis(1))));
        factory.setProxyTargetClass(true);
        QaSuggestionService proxied = factory.getProxy();

        try {
            proxied.suggest(ISSUE_ID, USER_ID);
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
        } finally {
            aspectLogger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("읽기가 막히면 AI 를 부르지 않는다")
    void neverCallsAiWhenReadFails() {
        when(issueRequirementReader.read(ISSUE_ID, USER_ID))
                .thenThrow(new BusinessException(DiagnosisErrorCode.ISSUE_NOT_FOUND));

        assertThatThrownBy(() -> service.suggest(ISSUE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", DiagnosisErrorCode.ISSUE_NOT_FOUND);
        verify(qaSuggestionClient, never()).suggest(any());
    }
}
