# 개발자 포트폴리오 기능 구현 계획

> 기준: `https://komit.site/mock/service/docs` (2026-09-21 조회), `docs/BACKEND_CONVENTIONS.md`, `docs/RULES.md`, 서비스 ERD.
> 브랜치 예시: `S15P21A201-<이슈>/be-api/feature/developer-portfolio`

## 1. 결론 먼저

포트폴리오는 **독립 API가 아닙니다.** 목 명세에서 작업물(portfolio item)은 아래 세 API의 일부이고, 썸네일은 업로드 3콜 API에 기댑니다.

| 순서 | API | 영역 | 포트폴리오와의 관계 | 서비스 구현 상태 |
| --- | --- | --- | --- | --- |
| ① | `GET /api/users/me/developer-profile` | 8 | `portfolioItems[]` 조회 | 없음 |
| ② | `PUT /api/users/me/developer-profile` | 8 | `portfolioItems[]` 전체 치환 저장 (프로필 생성 포함) | 없음 |
| ③ | `GET /api/developers/{developerId}` | 3 | 공개 프로필의 `portfolioItems[]` | 없음. **영역 3 담당자 소관** |
| ④ | `POST /api/uploads` → 저장소 `PUT` → `POST /api/uploads/{uploadId}/complete` | 8 | 썸네일 파일(`purpose=PORTFOLIO_IMAGE`) 발급·검증, `thumbnailUrl` 생성 | 없음. **마지막에 붙인다** |

①·②가 본체입니다. ④는 파일 API를 따로 달 때 함께 하고, 그때까지는 썸네일을 "식별자만 저장, 주소는 null"로 둡니다. ③은 우리 영역이 아니므로 응답 모양만 맞춰 둡니다.

## 2. 확정된 결정

| 항목 | 결정 | 반영할 곳 |
| --- | --- | --- |
| 작업물 순서 | **바꾸지 않는다.** 등록순(id 오름차순) 고정 | 규칙서 `R-DEV-02`의 "순서를 바꿀 수 있다" 문구 수정 필요 |
| 개발자 목록 카드의 작업물 | **보여주지 않는다** | 규칙서 `R-DEV-02`의 "요약에 상위 3건" 문구 삭제 필요. 영역 3 응답은 지금 그대로 |
| 제목·링크 길이 | **DB 기준.** 제목 200자, 링크 2048자 | 목 `WritePortfolioItemRequest`에 `max_length` 추가, 실서버 DTO `@Size(max=…)` |
| "의뢰인이 공개에 동의한 사진만" 문장 | **삭제.** 올리는 것이 곧 동의 | 목 `POST /api/uploads` 설명에서 문장 제거 |
| 썸네일 파일 검증 실패 응답 | 남의 파일·없는 파일·검증 미완료 파일 모두 **400 `FILE_NOT_ATTACHABLE`**. 프로필 사진 바꾸기가 이미 쓰는 코드라 같은 코드로 맞췄고, 파일 존재를 드러내지 않기 위해 셋을 가르지 않는다 | 실서버 `FileLookupService`, 목 PUT 응답에 400 추가 |
| 없는 분야·기술 코드 | **404 `CODE_NOT_FOUND`** 신설 | 목 PUT 응답에 404 추가, 실서버 `CodeErrorCode` |
| 썸네일 이미지 한도 | `image/png`·`image/jpeg`·`image/webp`, **5MB** | ④ 구현 시 설정값. 규칙서 「미확정」이 아니라 확정 규칙으로 추가 |
| 썸네일 주소 유효 시간 | **15분** | ④ 구현 시 설정값 |
| 프로필 이중 생성 충돌 | `developers.user_id` unique 위반이면 **재조회해 수정으로 처리, 200** | ② 저장 서비스 |
| 작업물 최대 개수 | 제한 없음 | — |

규칙서(`RULES.md`) 수정은 공용 문서라 **별도 MR로 먼저 말하고 진행**합니다. 이 문서의 결정만으로 바꾸지 않습니다.

## 3. 데이터 모델

ERD `portfolio_items`와 현재 엔티티 `domain/developer/entity/PortfolioItem`은 이미 일치합니다. 컬럼 추가는 없습니다.

| 컬럼 | 엔티티 | 비고 |
| --- | --- | --- |
| `id` | `id` | 응답 `portfolioItemId` |
| `thumbnail_id` | `thumbnailId` | `files.file_id` FK. 응답 `thumbnailFileId` |
| `title` (200) | `title` | 필수 |
| `description` (500) | `description` | null 허용 |
| `link_url` (2048) | `linkUrl` | null 허용 |
| `developer_id` | `developerId` | 소유자 판정 기준 |
| 소프트 삭제 컬럼 | `SoftDeleteEntity` | 삭제는 `markDeleted()` |

엔티티에 추가할 것은 **행위 메서드**뿐입니다.

```java
public static PortfolioItem create(Integer developerId, String title, String description, String linkUrl, Integer thumbnailId)
public void update(String title, String description, String linkUrl, Integer thumbnailId)
public void remove(LocalDateTime now)   // markDeleted 래핑
```

`thumbnailUrl`은 ERD에 없는 값이지만 목 설명대로 "저장된 값이 아니라 응답을 만들 때마다 새로 만드는" 임시 주소라 응답에 담을 수 있습니다. 컬럼을 만들지 않습니다.

## 4. API별 계획

### ① `GET /api/users/me/developer-profile` — 내 프로필 조회

| 항목 | 내용 |
| --- | --- |
| 인증 | 필수. `@AuthenticationPrincipal AuthenticatedUser` → `userId` |
| 응답 | `MyDeveloperProfileResponse`. 이 문서 범위는 `portfolioItems: MyPortfolioItemResponse[]` (`portfolioItemId, title, description, linkUrl, thumbnailFileId, thumbnailUrl`) |
| 실패 | `403 NO_DEVELOPER_PROFILE` (`DeveloperErrorCode`) — `developers` 행이 없거나 삭제된 경우. 404가 아니라 403인 것은 목 명세 그대로 |
| 정렬 | 등록순. `id ASC` |
| `thumbnailUrl` | ④ 전까지 **항상 null**. ④가 붙으면 `FileService`가 한 번에 만든다 |

패키지 구성 (`domain/developer`):

```text
controller/  MyDeveloperProfileController      /api/users/me/developer-profile (GET, PUT)
service/     DeveloperProfileQueryService      조회 조립
             DeveloperProfileCommandService    저장 (②)
             PortfolioItemService              작업물 치환·조회
repository/  PortfolioItemRepository           List<PortfolioItem> findByDeveloperIdAndDeletedFalseOrderByIdAsc(Integer)
dto/response MyDeveloperProfileResponse, MyPortfolioItemResponse
DeveloperErrorCode                              NO_DEVELOPER_PROFILE
```

조회 흐름:

1. `DeveloperRepository.findByUserIdAndDeletedFalse(userId)` 없으면 `NO_DEVELOPER_PROFILE`.
2. `PortfolioItemRepository`로 작업물 목록을 가져온다. 단순 조건 조회라 QueryDSL을 쓰지 않는다 (컨벤션 13.3).
3. `MyPortfolioItemResponse.of(item, thumbnailUrl)`로 변환. ④ 전에는 `thumbnailUrl = null`.

경력·기술·가격표·평점 조립은 같은 서비스에서 하되 이 문서 범위 밖입니다. 포트폴리오만 먼저 붙일 수 있게 `PortfolioItemService.listMine(developerId)`를 따로 두고 `DeveloperProfileQueryService`가 합칩니다.

### ② `PUT /api/users/me/developer-profile` — 내 프로필 저장

프로필 전체를 통째로 받는 API입니다. 포트폴리오 부분만 떼어 적습니다.

| 항목 | 내용 |
| --- | --- |
| 요청 | `WriteDeveloperProfileRequest.portfolioItems: List<@Valid WritePortfolioItemRequest>` (필수, 빈 배열 허용) |
| 항목 DTO | `portfolioItemId: Integer`(null=신규), `title: String @NotNull @Size(max=200)`, `description: String @Size(max=500)`, `linkUrl: String @Size(max=2048)`, `thumbnailFileId: Integer`. 뒤 세 필드는 null 허용이지만 **키 자체는 있어야 한다**(목 `required`) — Jackson 기본 동작이 이미 그렇다 |
| 상태 코드 | 프로필 새로 만들면 **201**, 고쳤으면 **200**. 서비스가 `created` 플래그를 돌려주고 컨트롤러가 `ResponseEntity`로 상태만 고른다 |
| 실패 | `400 FILE_NOT_ATTACHABLE`(붙일 수 없는 `thumbnailFileId`), `401`, `403 FORBIDDEN`(남의 `portfolioItemId`), `404 CODE_NOT_FOUND`, `422 INVALID_REQUEST` |

치환 알고리즘 (`PortfolioItemService.replaceAll(developerId, userId, requests, now)`):

```text
existing = findByDeveloperIdAndDeletedFalseOrderByIdAsc(developerId)   → Map<id, entity>
1. 요청의 portfolioItemId 중 existing에 없는 id가 하나라도 있으면 → 403 FORBIDDEN
   (삭제된 내 항목·남의 항목·없는 항목 모두 같은 응답)
2. 요청의 thumbnailFileId(null 아님) 전부를 한 번에 검증
   → files.status = COMPLETED 이고 files.user_id = userId 가 아니면 400 FILE_NOT_ATTACHABLE
   ④ 전에도 이 검증은 한다. files 엔티티·리포지토리가 이미 있으므로
   domain/file/service/FileLookupService.assertAttachable(userId, ids) 만 얇게 만든다 (구현됨)
3. 요청 순회
   - id null   → PortfolioItem.create(...) 저장. "새 줄은 뒤에 붙는다" = IDENTITY id가 크므로 자연 충족
   - id 있음   → entity.update(...). 순서는 id 그대로
4. existing 중 요청에 없는 id → remove(now)  (소프트 삭제)
5. 저장 뒤 ①의 조회 로직으로 응답을 다시 만든다
```

같은 트랜잭션 안에서 경력·가격표·기술도 치환하며, 프로필이 없으면 `Developer`·`DeveloperProfile` 생성 + `users.has_dev_profile = true` + `settings.is_seller_terms_agreed = true`까지 한 트랜잭션입니다. 이 부분은 `user`·`setting` 도메인 서비스에 메서드를 추가해 호출합니다 (`UserService.markDevProfile(userId)`, `SettingService.agreeSellerTerms(userId)`). 다른 도메인의 엔티티를 직접 만지지 않습니다.

동시성: 같은 사용자의 저장 요청이 두 인스턴스에 동시에 닿아 둘 다 "프로필 없음"으로 판단하면 `developers.user_id` unique 제약이 두 번째를 막습니다. `DataIntegrityViolationException`을 잡아 재조회하고 수정 경로로 다시 태워 200을 돌려줍니다.

### ③ `GET /api/developers/{developerId}` — 공개 프로필 (영역 3, 참고만)

- `PortfolioItemResponse`(`portfolioItemId, title, description, linkUrl, thumbnailFileId`)는 ①의 응답에서 `thumbnailUrl`만 뺀 모양입니다.
- ①에서 만든 `PortfolioItemService.listPublic(developerId)`를 영역 3 담당자가 그대로 호출하면 됩니다. 정렬 규칙도 같습니다.
- `SecurityConfig`에 이미 `GET /api/developers/*`가 열려 있습니다. 우리 쪽 변경 없음.

### ④ 업로드 3콜 — 파일 API와 함께 마지막에

`domain/file`에 서비스·컨트롤러를 만들 때 포트폴리오가 필요로 하는 것은 두 가지뿐입니다.

- ②의 `FileLookupService.assertAttachable`을 그대로 쓴다.
- `FileService.presignedUrls(List<Integer>)`로 `thumbnailUrl`을 **한 번에** 만들어 ①에 끼운다. 항목마다 호출하지 않는다.

| 항목 | 내용 |
| --- | --- |
| 경로 | `POST /api/uploads` (201), `POST /api/uploads/{uploadId}/complete` (200). 저장소 `PUT`은 실서버에 없음 |
| `PORTFOLIO_IMAGE` 한도 | `image/png`·`image/jpeg`·`image/webp`, 5MB (2절 결정) |
| 주소 유효 시간 | 15분 (2절 결정) |
| 외부 의존 | MinIO. `build.gradle`에 `io.minio:minio`, `deploy/local/docker-compose.yml`에 MinIO 추가는 인프라 담당과 협의 |

상세 설계는 파일 API 작업 때 따로 씁니다.

## 5. 목 서버 수정 (영역 8)

| 위치 | 수정 |
| --- | --- |
| `WritePortfolioItemRequest` | `title`에 `max_length=200`, `linkUrl`에 `max_length=2048` |
| `PUT /api/users/me/developer-profile` 설명 | 422 케이스에 "제목이 200자를 넘을 때" 추가. 403 케이스에 "붙일 수 없는 `thumbnailFileId`(남의 파일·검증 전 파일)일 때" 추가 |
| `POST /api/uploads` 설명 | "작업물 사진은 의뢰인이 공개에 동의한 것만…" 문단 삭제 |
| `mock/tests/` | 프로필 저장 422(제목 초과) 테스트 추가 |

## 6. 작업 순서

1. 목 수정(5절) → MR.
2. 규칙서 `R-DEV-02` 수정 제안 (순서 고정, 요약 노출 없음) → 팀에 말하고 별도 MR.
3. 서비스: `DeveloperErrorCode`, `PortfolioItem` 행위 메서드, `PortfolioItemRepository` 쿼리 메서드, `FileLookupService.assertAttachable`, `PortfolioItemService` + 단위 테스트.
4. ① 컨트롤러·조회 서비스 + `@WebMvcSecurityTest`.
5. ② 저장 서비스 + 리포지토리 통합 테스트(Testcontainers): 치환·소프트 삭제·남의 id 403·붙일 수 없는 파일 403·이중 생성 충돌.
6. `https://komit.site/mock/service`와 로컬에 같은 요청을 보내 상태·`code`·`message`·`errors` 순서 대조.
7. (나중) ④ 파일 API + MinIO, `thumbnailUrl` 채우기.

## 7. 완료 기준

- 목과 같은 요청에 같은 상태·본문 (200/201 구분, 403 두 종류, 422 `errors` 순서)
- 남의 `portfolioItemId`·붙일 수 없는 파일이 저장되지 않음
- 빠진 항목이 소프트 삭제되고 `is_deleted`·`deleted_at`·`record_status`가 함께 바뀜
- 목록 응답이 등록순이고 새 항목이 뒤에 붙음
- `gradlew build` 통과, SonarQube 신규 이슈 없음
