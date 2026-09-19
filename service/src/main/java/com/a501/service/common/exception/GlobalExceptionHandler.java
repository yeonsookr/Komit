package com.a501.service.common.exception;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.a501.service.common.exception.ErrorResponse.FieldErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * 전역 예외 처리기. 실패 응답의 상태·{@code code}·{@code message}·JSON 구조는 목 서버(/service/docs)와 같아야 합니다. (BACKEND_CONVENTIONS 12.1)
 *
 * <p>Spring MVC 예외는 {@link ResponseEntityExceptionHandler}가 상태 코드를 정하고, 여기서는 본문만 계약 형태로 바꿉니다.
 * 요청 형식 오류는 422 {@code INVALID_REQUEST}에 {@code errors[]}를 실어 내보내며, 검증 문구는 {@code messages.properties}의
 * Bean Validation 메시지를 그대로 씁니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    /** 목(Pydantic) 문구. 값이 어떤 타입이어야 하는지만 말하고 보낸 값은 담지 않습니다. */
    static final String MISSING = "필수 항목입니다.";
    static final String INTEGER = "정수여야 합니다.";
    static final String STRING = "문자열이어야 합니다.";
    static final String BOOLEAN = "true 또는 false여야 합니다.";
    static final String JSON_INVALID = "JSON 형식이 올바르지 않습니다.";
    static final String INVALID = "값이 올바르지 않습니다.";
    /** 본문 전체를 가리키는 필드명. */
    static final String BODY = "body";

    /** 경로 단계의 404·405는 목 명세가 코드를 정하지 않아 목(Starlette) 기본 본문을 그대로 씁니다. */
    private static final Map<String, String> NOT_FOUND = Map.of("detail", "Not Found");
    private static final Map<String, String> METHOD_NOT_ALLOWED = Map.of("detail", "Method Not Allowed");

    // ------------------------------------------------------------------ 도메인·보안·예상 밖

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException exception) {
        return error(exception.getErrorCode());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied() {
        return error(CommonErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication() {
        return error(CommonErrorCode.UNAUTHORIZED);
    }

    /** 멀티파트를 읽지 못했거나(boundary 없음 등) Content-Type이 파싱되지 않을 때. 목은 본문을 읽지 못한 것으로 보고 422를 냅니다. */
    @ExceptionHandler({MultipartException.class, InvalidMediaTypeException.class})
    ResponseEntity<Object> handleUnreadableRequest() {
        return invalid(List.of(new FieldErrorResponse(BODY, INVALID)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception exception) {
        // {@code Content-Type: application/*}는 Spring이 본문을 읽기 전에 IllegalArgumentException으로 거절합니다. 클라이언트 잘못이므로 422입니다.
        if (exception instanceof IllegalArgumentException
                && String.valueOf(exception.getMessage()).startsWith("Content-Type cannot contain wildcard")) {
            return handleUnreadableRequest();
        }
        log.error("Unexpected request processing failure", exception);
        return error(CommonErrorCode.INTERNAL_ERROR);
    }

    // ------------------------------------------------------------------ Spring MVC 예외 → 계약 본문

    /** 상태는 Spring이 정한 대로 두고 본문만 바꿉니다. 404·405는 목 기본 본문, 그 밖의 4xx는 422 INVALID_REQUEST, 5xx는 500입니다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            return handleUnexpected(exception);
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return json(HttpStatus.NOT_FOUND, headers, NOT_FOUND);
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return json(HttpStatus.METHOD_NOT_ALLOWED, headers, METHOD_NOT_ALLOWED);
        }
        return invalid(List.of(new FieldErrorResponse(BODY, INVALID)));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalid(fieldErrors(exception.getBindingResult()));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bean) {
                errors.addAll(fieldErrors(bean));
                continue;
            }
            String name = parameterName(result.getMethodParameter());
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldErrorResponse(name, error.getDefaultMessage()));
            }
        }
        return invalid(errors);
    }

    /** 본문이 없거나, JSON이 깨졌거나, 필드 타입이 맞지 않을 때. 목과 같은 필드·문구로 가릅니다. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = exception.getCause();
        if (cause instanceof StreamReadException
                || (cause instanceof DatabindException databind && databind.getPath().isEmpty()
                        && String.valueOf(databind.getOriginalMessage()).startsWith("No content to map"))) {
            // 깨진 JSON, 또는 공백만 있는 본문
            return invalid(List.of(new FieldErrorResponse(BODY, JSON_INVALID)));
        }
        if (cause instanceof DatabindException databind) {
            String field = databind.getPath().stream()
                    .map(reference -> reference.getPropertyName() != null
                            ? reference.getPropertyName() : String.valueOf(reference.getIndex()))
                    .collect(Collectors.joining("."));
            Class<?> target = databind instanceof MismatchedInputException mismatched ? mismatched.getTargetType() : null;
            return invalid(List.of(field.isEmpty()
                    ? new FieldErrorResponse(BODY, INVALID)
                    : new FieldErrorResponse(field, typeReason(target))));
        }
        return invalid(List.of(new FieldErrorResponse(BODY, MISSING)));
    }

    /** JSON이 아닌 Content-Type. 목은 본문을 모델로 읽지 못한 것으로 보고 422를 냅니다. */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalid(List.of(new FieldErrorResponse(BODY, INVALID)));
    }

    /** 경로·쿼리 값이 선언 타입으로 바뀌지 않을 때. {@code /api/default/abc} → {@code id: 정수여야 합니다.} */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = exception.getPropertyName() == null ? BODY : exception.getPropertyName();
        return invalid(List.of(new FieldErrorResponse(name, typeReason(exception.getRequiredType()))));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalid(List.of(new FieldErrorResponse(exception.getParameterName(), MISSING)));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return invalid(List.of(new FieldErrorResponse(exception.getRequestPartName(), MISSING)));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return error(CommonErrorCode.FILE_TOO_LARGE);
    }

    // ------------------------------------------------------------------ helpers

    /** 필드 오류 목록. {@code items[0].title} → {@code items.0.title}, 문구는 Bean Validation 메시지, 순서는 필드명순입니다. */
    private static List<FieldErrorResponse> fieldErrors(Errors errors) {
        return errors.getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new FieldErrorResponse(
                        error.getField().replaceAll("\\[(\\d+)]", ".$1"),
                        error.getDefaultMessage() == null ? INVALID : error.getDefaultMessage()))
                .toList();
    }

    /** 요청에 쓰인 이름({@code @RequestParam("page")})을 우선하고 없으면 자바 매개변수 이름입니다. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.name().isEmpty()) {
            return pathVariable.name();
        }
        return parameter.getParameterName();
    }

    /** 값이 선언 타입으로 읽히지 않을 때의 문구. 목이 구분하는 정수·문자열·불리언·허용 목록만 가르고 나머지는 공통 문구입니다. */
    static String typeReason(Class<?> type) {
        if (type == null) {
            return INVALID;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class
                || type == Integer.class || type == Long.class || type == Short.class || type == Byte.class
                || type == BigInteger.class) {
            return INTEGER;
        }
        if (type == boolean.class || type == Boolean.class) {
            return BOOLEAN;
        }
        if (CharSequence.class.isAssignableFrom(type)) {
            return STRING;
        }
        if (type.isEnum()) {
            // Pydantic literal_error 표기: 'A', 'B' or 'C'
            String[] names = Arrays.stream(type.getEnumConstants()).map(constant -> "'" + constant + "'").toArray(String[]::new);
            String expected = names.length == 1 ? names[0]
                    : String.join(", ", Arrays.copyOf(names, names.length - 1)) + " or " + names[names.length - 1];
            return expected + " 중 하나여야 합니다.";
        }
        return INVALID;
    }

    private static ResponseEntity<Object> error(ErrorCode errorCode) {
        return json(errorCode.status(), new HttpHeaders(), ErrorResponse.of(errorCode));
    }

    private static ResponseEntity<Object> invalid(List<FieldErrorResponse> errors) {
        return json(CommonErrorCode.INVALID_REQUEST.status(), new HttpHeaders(), ErrorResponse.invalid(errors));
    }

    /** 실패 응답은 Accept 헤더와 무관하게 항상 JSON입니다(목과 같음). Content-Type을 미리 정하면 Spring이 협상을 건너뜁니다. */
    private static ResponseEntity<Object> json(HttpStatusCode status, HttpHeaders headers, Object body) {
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
