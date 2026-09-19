package com.a501.service.common.logging;

import java.time.Duration;

import com.a501.service.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 컨트롤러·서비스 호출의 시작·종료·소요 시간을 남기는 AOP 로깅. (BACKEND_CONVENTIONS 18)
 *
 * <p>계층별 기본 수준은 다음과 같습니다. 정상 흐름은 {@code error}로 남기지 않습니다.
 * <ul>
 *   <li>컨트롤러({@code domain.*.controller}): 완료를 INFO. 요청 하나에 한 줄이 남아 트래픽과 응답 시간을 보는 기준이 됩니다.</li>
 *   <li>서비스({@code domain.*.service}): 시작·완료를 DEBUG. 운영({@code com.a501: INFO})에서는 꺼지고 로컬에서만 보입니다.</li>
 *   <li>느린 호출: 어느 계층이든 {@link LoggingProperties#slowCallThreshold()}를 넘으면 WARN.</li>
 *   <li>{@link BusinessException}: 계약된 실패이므로 코드만 INFO로 남기고 다시 던집니다. 스택은 남기지 않습니다.</li>
 *   <li>그 밖의 예외: 클래스 이름만 DEBUG로 남기고 다시 던집니다. 스택 트레이스는 전역 예외 처리기가 한 번만 기록합니다.</li>
 * </ul>
 *
 * <p>메서드 인자와 반환값은 어느 수준에서도 남기지 않습니다. 토큰·계좌번호·본문이 섞일 수 있고, 값 노출은 로그 규칙 위반입니다.
 * 리포지토리 계층은 여기서 다루지 않습니다. SQL은 {@code org.hibernate.SQL} 로거가 맡습니다.
 *
 * <p>프록시 기반이므로 같은 빈 안에서 자기 메서드를 부르는 내부 호출은 기록되지 않습니다. 컨트롤러의 {@code @InitBinder}·
 * {@code @ModelAttribute} 메서드는 요청마다 프레임워크가 부르는 준비 작업이라 핸들러 로그와 섞이지 않도록 제외합니다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {
    /** 도메인 패키지의 {@code @RestController} 빈에 선언된 핸들러 메서드. 바인더·모델 준비 메서드는 제외합니다. */
    static final String CONTROLLER_POINTCUT = "within(com.a501.service.domain..controller..*)"
            + " && @within(org.springframework.web.bind.annotation.RestController)"
            + " && !@annotation(org.springframework.web.bind.annotation.InitBinder)"
            + " && !@annotation(org.springframework.web.bind.annotation.ModelAttribute)";
    /** 도메인 패키지의 {@code @Service} 빈에 선언된 모든 메서드. */
    static final String SERVICE_POINTCUT = "within(com.a501.service.domain..service..*)"
            + " && @within(org.springframework.stereotype.Service)";

    private final LoggingProperties properties;

    @Pointcut(CONTROLLER_POINTCUT)
    void controllerLayer() {
    }

    @Pointcut(SERVICE_POINTCUT)
    void serviceLayer() {
    }

    /**
     * 컨트롤러 호출을 감쌉니다.
     *
     * @param joinPoint 대상 호출
     * @return 대상 메서드의 반환값 그대로
     * @throws Throwable 대상 메서드가 던진 예외 그대로
     */
    @Around("controllerLayer()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        return proceed(joinPoint, Layer.CONTROLLER);
    }

    /**
     * 서비스 호출을 감쌉니다.
     *
     * @param joinPoint 대상 호출
     * @return 대상 메서드의 반환값 그대로
     * @throws Throwable 대상 메서드가 던진 예외 그대로
     */
    @Around("serviceLayer()")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        return proceed(joinPoint, Layer.SERVICE);
    }

    private Object proceed(ProceedingJoinPoint joinPoint, Layer layer) throws Throwable {
        String call = joinPoint.getSignature().toShortString();
        long startedAt = System.nanoTime();
        log.debug("{} start {}", layer, call);
        try {
            Object result = joinPoint.proceed();
            logCompleted(layer, call, elapsedMillis(startedAt));
            return result;
        } catch (BusinessException exception) {
            log.info("{} rejected {} code={} elapsedMs={}",
                    layer, call, exception.getErrorCode().code(), elapsedMillis(startedAt));
            throw exception;
        } catch (Throwable throwable) {
            log.debug("{} failed {} exception={} elapsedMs={}",
                    layer, call, throwable.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw throwable;
        }
    }

    private void logCompleted(Layer layer, String call, long elapsedMs) {
        if (elapsedMs >= properties.slowCallThreshold().toMillis()) {
            log.warn("{} slow {} elapsedMs={} thresholdMs={}",
                    layer, call, elapsedMs, properties.slowCallThreshold().toMillis());
            return;
        }
        if (layer == Layer.CONTROLLER) {
            log.info("{} done {} elapsedMs={}", layer, call, elapsedMs);
        } else {
            log.debug("{} done {} elapsedMs={}", layer, call, elapsedMs);
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    /** 로그 줄 앞에 붙는 계층 표시. */
    enum Layer {
        CONTROLLER, SERVICE
    }
}
