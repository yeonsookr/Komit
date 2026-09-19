# AGENTS.md — service (be-api)

코밋 서비스 서버(`service/`)에서 작업하는 에이전트가 따르는 규칙입니다. 저장소 루트 `AGENTS.md`와 `docs/BACKEND_CONVENTIONS.md`를 전제로 하며, 이 문서는 그중 **매번 놓치기 쉬운 것**만 다시 적습니다.

## API를 새로 붙일 때 — 예외 처리와 검증은 이미 있다. 새로 만들지 마라

전역 예외 처리기 `common/exception/GlobalExceptionHandler`가 모든 컨트롤러에 자동으로 걸립니다. 컨트롤러·서비스에 `try/catch`, `@ExceptionHandler`, `ResponseEntity.status(...)` 로 실패 응답을 만들지 않습니다. 할 일은 두 가지뿐입니다.

### 1. 실패는 `BusinessException`으로 던진다

```java
// domain/<도메인>/<Domain>ErrorCode.java — 상태·코드·문구는 목 명세(/service/docs)의 해당 API 설명을 글자 그대로 옮긴다
public enum ContractErrorCode implements ErrorCode {
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "계약서를 찾을 수 없습니다."),
    CONTRACT_ALREADY_SIGNED(HttpStatus.CONFLICT, "CONTRACT_ALREADY_SIGNED", "이미 체결된 계약서는 고칠 수 없습니다.");
    // 필드·생성자·구현 메서드는 domain/auth/AuthErrorCode.java 와 같은 모양
}

// 서비스
throw new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND);
// → 404 {"code":"CONTRACT_NOT_FOUND","message":"계약서를 찾을 수 없습니다."}
```

- 공통 코드(`INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `FILE_TOO_LARGE`, `INTERNAL_ERROR`)는 `CommonErrorCode`에 있습니다. 여기에 도메인 코드를 넣지 않습니다.
- `ResponseStatusException`, `IllegalArgumentException`, `IllegalStateException`으로 클라이언트 오류를 내지 않습니다. 500으로 나갑니다.
- 목 명세에 없는 코드를 만들지 않습니다. 필요하면 목에 먼저 추가합니다.

### 2. 요청 DTO에는 `@Valid`와 제약 애너테이션만 붙인다

```java
@PostMapping
ReviewResponse write(@PathVariable Long contractId, @Valid @RequestBody WriteReviewRequest request)

public record WriteReviewRequest(
        @NotNull @Min(1) @Max(5) Integer devScore,   // 목: ge=1, le=5
        @NotNull @Min(1) @Max(5) Integer expScore,
        @Size(max = 1000) String content) {}         // 목: max_length=1000
```

위반 시 422 `INVALID_REQUEST` + `errors[{field, reason}]`가 자동으로 나갑니다. 문구는 `src/main/resources/messages.properties`가 정하므로 DTO에 `message=`를 직접 쓰지 않습니다. 단, 아래 세 경우만 지정된 키를 씁니다.

| 목 명세 | 자바 |
| --- | --- |
| 필수 | `@NotNull` — `@NotBlank` 금지(목은 빈 문자열을 받음) |
| `ge=N` / `le=N` | `@Min(N)` / `@Max(N)` |
| 문자열 `max_length=N` | `@Size(max = N)` |
| 문자열 `min_length=N` | `@Size(min = N, message = "{komit.minLength}")` |
| 목록 `min_length=N` | `@Size(min = N, message = "{komit.minItems}")` |
| 목록 `max_length=N` | `@Size(max = N, message = "{komit.maxItems}")` |
| `pattern=` | `@Pattern(regexp = ...)` |
| `Literal[...]` | enum 타입. 상수 이름을 목 값과 같게 |
| `int` / `str` / `bool` | `Integer`·`Long` / `String` / `Boolean` — 필수 숫자는 `int`가 아니라 `Integer` + `@NotNull` |
| 중첩 객체·목록 항목 | `@Valid` (`List<@Valid Item> items`) — 없으면 안쪽이 검증되지 않음 |
| 쿼리·경로 파라미터 | `@RequestParam @Min(1) int page` 처럼 파라미터에 직접 |

전체 대응표와 처리기가 만드는 응답 목록은 `docs/BACKEND_CONVENTIONS.md` 10.1과 12.1에 있습니다.

## 검증 방법

- 컨트롤러 테스트는 `common/security/WebMvcSecurityTest`로 보안 체인까지 태웁니다. 예시는 `domain/auth/controller/AuthControllerTest`.
- 실패 응답을 목과 대조할 때는 같은 요청을 `https://komit.site/mock/service`와 로컬에 보내 상태·`code`·`message`·`errors` 순서까지 비교합니다.
- 로컬 실행은 `deploy/local/docker-compose.yml`입니다. 이 저장소는 JDK 21 툴체인을 요구합니다.
