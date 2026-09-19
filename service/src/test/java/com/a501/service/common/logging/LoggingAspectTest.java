package com.a501.service.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.a501.service.common.exception.BusinessException;
import com.a501.service.domain.defaults.controller.DefaultController;
import com.a501.service.domain.defaults.dto.response.DefaultResponse;
import com.a501.service.domain.defaults.entity.Default;
import com.a501.service.domain.defaults.repository.DefaultRepository;
import com.a501.service.domain.defaults.service.DefaultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.WebDataBinder;

/**
 * 스프링 컨텍스트 없이 AspectJ 프록시로 애스펙트를 감아, 포인트컷이 도메인 서비스에 걸리고 반환값·예외를 그대로 통과시키며
 * 규칙대로 한 줄씩 남기는지 확인합니다. 대상은 실제 {@link DefaultService}입니다.
 */
class LoggingAspectTest {
    private final Logger aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level originalLevel = aspectLogger.getLevel();

    private DefaultRepository defaultRepository;

    @BeforeEach
    void setUp() {
        appender.start();
        aspectLogger.addAppender(appender);
        aspectLogger.setLevel(Level.DEBUG);
        defaultRepository = mock(DefaultRepository.class);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
        aspectLogger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("서비스 호출은 반환값을 그대로 통과시키고 시작·완료를 DEBUG로 남긴다. 인자 값은 남기지 않는다")
    void logsServiceCallWithoutArguments() {
        Default entity = Default.of("secret-looking-value");
        ReflectionTestUtils.setField(entity, "id", 7);
        when(defaultRepository.findByIdAndDeletedFalse(7)).thenReturn(Optional.of(entity));
        DefaultService proxied = proxy(Duration.ofSeconds(1));

        DefaultResponse response = proxied.getDefault(7);

        assertThat(response).isEqualTo(new DefaultResponse(7, "secret-looking-value"));
        List<String> lines = messages();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("SERVICE start DefaultService.getDefault(..)");
        assertThat(lines.get(1)).startsWith("SERVICE done DefaultService.getDefault(..) elapsedMs=");
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
        assertThat(String.join("\n", lines)).doesNotContain("7", "secret-looking-value");
    }

    @Test
    @DisplayName("BusinessException은 코드만 INFO로 남기고 그대로 다시 던진다")
    void logsBusinessRejectionAndRethrows() {
        when(defaultRepository.findByIdAndDeletedFalse(any())).thenReturn(Optional.empty());
        DefaultService proxied = proxy(Duration.ofSeconds(1));

        assertThatThrownBy(() -> proxied.getDefault(2)).isInstanceOf(BusinessException.class);

        ILoggingEvent rejected = appender.list.get(appender.list.size() - 1);
        assertThat(rejected.getLevel()).isEqualTo(Level.INFO);
        assertThat(rejected.getFormattedMessage())
                .startsWith("SERVICE rejected DefaultService.getDefault(..) code=DEFAULT_NOT_FOUND elapsedMs=");
    }

    @Test
    @DisplayName("예상 밖 예외는 클래스 이름만 DEBUG로 남기고 스택은 남기지 않는다. 스택은 전역 처리기가 한 번 기록한다")
    void logsUnexpectedFailureWithoutStackTrace() {
        when(defaultRepository.findByIdAndDeletedFalse(any())).thenThrow(new IllegalStateException("db down"));
        DefaultService proxied = proxy(Duration.ofSeconds(1));

        assertThatThrownBy(() -> proxied.getDefault(1)).isInstanceOf(IllegalStateException.class);

        ILoggingEvent failed = appender.list.get(appender.list.size() - 1);
        assertThat(failed.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(failed.getFormattedMessage())
                .startsWith("SERVICE failed DefaultService.getDefault(..) exception=IllegalStateException elapsedMs=");
        assertThat(failed.getThrowableProxy()).isNull();
        assertThat(failed.getFormattedMessage()).doesNotContain("db down");
    }

    @Test
    @DisplayName("기준 시간을 넘긴 호출은 WARN으로 남긴다")
    void warnsOnSlowCall() {
        when(defaultRepository.findByIdAndDeletedFalse(any())).thenAnswer(invocation -> {
            Thread.sleep(20);
            return Optional.of(Default.of("default"));
        });
        DefaultService proxied = proxy(Duration.ofMillis(1));

        proxied.getDefault(1);

        ILoggingEvent slow = appender.list.get(appender.list.size() - 1);
        assertThat(slow.getLevel()).isEqualTo(Level.WARN);
        assertThat(slow.getFormattedMessage()).startsWith("SERVICE slow DefaultService.getDefault(..) elapsedMs=");
        assertThat(slow.getFormattedMessage()).endsWith("thresholdMs=1");
    }

    @Test
    @DisplayName("컨트롤러 포인트컷은 핸들러에만 걸리고 @InitBinder 준비 메서드와 서비스는 제외한다")
    void controllerPointcutMatchesHandlersOnly() throws Exception {
        AspectJExpressionPointcut controller = new AspectJExpressionPointcut();
        controller.setExpression(LoggingAspect.CONTROLLER_POINTCUT);
        AspectJExpressionPointcut service = new AspectJExpressionPointcut();
        service.setExpression(LoggingAspect.SERVICE_POINTCUT);

        assertThat(controller.matches(DefaultController.class.getDeclaredMethod("getDefault", Integer.class),
                DefaultController.class)).isTrue();
        assertThat(controller.matches(DefaultController.class.getDeclaredMethod("registerDefaultSortEditor", WebDataBinder.class),
                DefaultController.class)).isFalse();
        assertThat(controller.matches(DefaultService.class.getMethod("getDefault", Integer.class), DefaultService.class)).isFalse();
        assertThat(service.matches(DefaultService.class.getMethod("getDefault", Integer.class), DefaultService.class)).isTrue();
    }

    private DefaultService proxy(Duration slowCallThreshold) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new DefaultService(defaultRepository));
        factory.addAspect(new LoggingAspect(new LoggingProperties(slowCallThreshold)));
        factory.setProxyTargetClass(true);
        return factory.getProxy();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
