# Komit 백엔드 개발 컨벤션

> 팀원이 같은 기준으로 설계·구현·리뷰하기 위한 백엔드 공통 규칙입니다.  
> 기준일: 2026-09-11

## 1. 문서 목적과 적용 범위

이 문서는 Komit 백엔드의 다음 항목을 통일합니다.

- Java/Spring/PostgreSQL 기술 스택과 버전
- 서로 독립된 Komit 서버와 Escrow 서버의 경계
- 각 서버의 2개 이상 인스턴스 운영을 위한 이중화 원칙
- DDD 스타일의 도메인 중심 패키지 구조
- Controller, Service, Repository, Entity, DTO의 책임
- `mock` 서버 Swagger를 단일 기준으로 하는 API 계약 관리 방식
- PostgreSQL 스키마 변경과 Flyway 마이그레이션
- 보안, 로깅, 테스트, 브랜치와 리뷰 기준

별도 합의가 없다면 신규 코드와 수정 코드 모두 이 문서를 따릅니다. 기존 코드가 이 문서와 다를 때는 관련 작업 범위 안에서 점진적으로 정리하며, 무관한 대규모 리팩터링을 한 PR에 섞지 않습니다.

## 2. 규칙의 우선순위

충돌이 발생하면 아래 순서로 판단합니다.

1. 확정된 ERD 및 외부 연동 기관의 공식 명세
2. `mock` 서버 Swagger에 합의된 API 계약
3. 이 백엔드 컨벤션
4. 개별 구현 코드와 개인 선호

ERD에 없는 필드가 API 구현에 필요하면 임의로 추가하지 않습니다. ERD 담당자 및 API 참여자와 먼저 합의하고 ERD와 목 명세를 수정한 뒤 실제 서버를 구현합니다.

## 3. 기술 스택과 고정 버전

| 구분 | 팀 기준 | 버전 관리 원칙 |
| --- | --- | --- |
| JDK | Eclipse Temurin 등 표준 OpenJDK | **Java 21 LTS** 고정 |
| Spring Boot | Web MVC 기반 | **4.1.1** 고정 |
| Spring Framework | Spring Boot BOM 사용 | 개별 버전을 적지 않음 |
| Spring Data JPA | Spring Boot BOM 사용 | 개별 버전을 적지 않음 |
| Spring Security | Spring Boot BOM 사용 | 개별 버전을 적지 않음 |
| Hibernate / Jackson | Spring Boot BOM 사용 | 개별 버전을 적지 않음 |
| API 구현 문서 | springdoc-openapi | **3.1.1** |
| 빌드 도구 | Gradle Wrapper | **9.7.1** |
| 데이터베이스 | PostgreSQL | **18.x**, 최초 고정 이미지 **18.6** |
| 동적 쿼리 | QueryDSL(jakarta) | **5.1.0** |
| DB 마이그레이션 | Flyway | Spring Boot BOM 사용 |
| 테스트 | JUnit 5, Spring Boot Test, Testcontainers | Spring Boot BOM 사용 |
| 코드 보조 | Lombok | Spring Boot BOM 사용 |

Spring Boot 4.1.1은 Java 17 이상과 Gradle 8.14 이상 또는 9.x를 지원하므로 Java 21과 Gradle 9.7.1 조합을 사용합니다. Spring Boot가 관리하는 라이브러리는 특별한 사유가 없는 한 버전을 개별 지정하지 않습니다.

PostgreSQL은 메이저 버전을 18로 고정하고, 보안 및 버그 수정이 포함된 최신 마이너 버전으로 정기 갱신합니다. 운영 이미지의 태그를 변경할 때는 마이그레이션과 회귀 테스트를 통과해야 합니다.

참고 문서:

- [Spring Boot 4.1.1 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot Web MVC](https://docs.spring.io/spring-boot/reference/web/index.html)
- [springdoc-openapi](https://springdoc.org/)
- [Gradle 릴리스](https://gradle.org/releases/)
- [PostgreSQL 버전 정책](https://www.postgresql.org/support/versioning/)
- [Testcontainers PostgreSQL 모듈](https://java.testcontainers.org/modules/databases/postgres/)

## 4. Gradle 기본 설정

새 Spring Boot 애플리케이션의 `build.gradle`은 다음 구성을 기준으로 합니다.

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.a501'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api'

    runtimeOnly 'org.postgresql:postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:testcontainers-postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

버전 변경은 개인 브랜치에서 임의로 하지 않습니다. 변경 이유, 호환성 확인 결과, 마이그레이션 영향을 PR에 기록하고 팀 리뷰를 받습니다. 버전 재현성을 위해 시스템에 설치된 Gradle이 아니라 저장소의 `gradlew`/`gradlew.bat`를 사용합니다.

## 5. 독립 서버 경계와 API 계약

### 5.1 Komit와 Escrow 분리

현재 `mock`은 다음 두 API 영역을 독립적으로 제공합니다.

- Komit 서비스: `/service`, Swagger `/service/docs`
- Escrow 서비스: `/escrow`, Swagger `/escrow/docs`

Komit 서버와 Escrow 서버는 논리적 모듈이 아니라 **서로 완전히 독립된 서버**입니다. 각각 별도의 Spring Boot 애플리케이션, 실행 프로세스, 배포 단위와 장애 경계를 가집니다. 저장소를 어떻게 배치하든 다음 경계를 지킵니다.

- 두 서버가 같은 애플리케이션 프로세스나 같은 Spring Context에서 실행되지 않습니다.
- 한 서버가 다른 서버의 Entity, Repository, Service 클래스 또는 내부 패키지를 참조하지 않습니다.
- 서로의 데이터베이스 테이블을 직접 조회하거나 join하지 않습니다.
- 서버 간 기능은 문서화된 HTTP API 또는 합의된 메시지 계약으로만 호출합니다.
- 인증, timeout, retry, 멱등성, 실패 응답을 서버 간 호출에서도 명시합니다.
- 한 서버의 장애가 다른 서버의 전체 장애로 번지지 않도록 호출 timeout을 짧고 명확하게 설정합니다.
- 두 서버를 하나의 분산 트랜잭션으로 묶지 않습니다. 필요하면 상태 보상, 재처리 또는 Outbox 기반 이벤트 전달을 사용합니다.

Escrow가 금융 거래 상태의 원본을 소유한다면 Komit는 필요한 Escrow 식별자와 화면 표시용 최소 정보만 보관합니다. 어느 서버가 어떤 데이터의 원본인지 API별로 명확히 정하고 양쪽에서 같은 상태를 독립적으로 수정하지 않습니다.

금융망 사용자 식별정보와 개인 계좌의 원본은 Komit Service가 소유합니다. 처음
회원가입할 때 Service가 SSAFY 금융망 `userKey`를 발급받고 별도 계좌 생성 API로
개인 계좌를 만든 뒤 두 값을 Service DB에 저장합니다. Service는 예치·지급·환불 등
금융 업무에 필요한 `userKey`와 계좌번호를 Escrow 요청에 담고, Escrow는 이를 금융망
호출에만 사용하며 자기 DB에 사용자 금융 식별정보나 개인 계좌 원장을 만들지 않습니다.

Escrow가 소유하는 것은 보관계좌, 계약별 가상계좌, 에스크로 상태와 거래 원장입니다.
예치 상태는 입금 검증, 보관계좌 정산, 내부 기록이 모두 확정되기 전까지
`DEPOSIT_PENDING`이며 전부 확정된 뒤에만 `ACTIVE`로 전이합니다.

프런트엔드는 목 서버와 실제 서버 사이에서 **base URL만 바꾸면 동작**해야 합니다. 따라서 실제 서버의 HTTP method, `/api` 이후 경로, query/path parameter, JSON 필드, 상태 코드와 응답 바디는 목 서버 Swagger 계약과 같아야 합니다.

예시:

```text
Mock base URL:  http://localhost:8080/service
Real base URL:  https://api.komit.example.com
Resource path:  /api/developers/{developerId}
```

`/service`와 `/escrow`는 목 서버가 서브 애플리케이션을 마운트하기 위한 접두사입니다. 각 목 라우터 및 실제 Controller의 리소스 경로에 이를 중복해서 넣지 않습니다.

### 5.2 API 명세의 단일 기준

프런트엔드 협업 단계의 공식 API 명세는 `mock` 서버 Swagger입니다. Komit 목 명세와 Escrow 목 명세도 서로 분리합니다. 각 Spring 서버의 springdoc 문서는 실제 구현 검증과 백엔드 내부 확인에 사용하되, 목 명세와 다른 별도 계약을 만들지 않습니다.

각 서버의 응답 규약은 다음 기준 API가 결정합니다.

- Komit: `mock/app/routers/service.py`의 `/service/api/default/{id}`와 `/service/api/defaults`
- Escrow: `mock/app/routers/escrow.py`의 `/escrow/api/default`

두 기준 API의 형식은 서로 다릅니다. Komit은 리소스 직접 반환과 `{code, message}` 에러 형식을 사용하고, Escrow는 SSAFY 금융망의 `Header`/`REC` 형식을 사용합니다. 같아야 하는 것은 두 서버의 모양이 아니라 **각 서버 내부의 일관성**입니다.

계약 변경 순서는 다음과 같습니다.

1. 사용자 플로우와 화면에서 필요한 동작을 확인합니다.
2. ERD에서 필드와 관계를 확인합니다.
3. `mock` 라우터와 Swagger 요청·응답·상태 코드를 작성하거나 수정합니다.
4. 프런트엔드와 백엔드가 명세를 리뷰하고 확정합니다.
5. 실제 Spring API를 동일한 계약으로 구현합니다.
6. MVC 테스트와 계약 테스트로 목 명세와 실제 응답의 일치를 검증합니다.

목 서버는 문서화와 프런트엔드 개발을 위한 도구입니다. 실제 데이터베이스, 상태 전이, 인증 판단과 비즈니스 로직을 목 서버에 구현하지 않습니다. 응답 예시는 하드코딩할 수 있지만, ERD에 없는 필드를 편의상 만들지 않습니다.

외부 SSAFY 금융 API에 존재하는 필드를 Escrow 목 응답에 사용해야 한다면 팀의 명시적 승인을 받고, 해당 필드가 ERD 외부 명세에서 왔음을 문서에 남깁니다.

## 6. 서버 이중화와 Single Writer 구조

Komit 서버와 Escrow 서버는 각각 최소 2개의 인스턴스로 운영합니다. 두 인스턴스는 같은 애플리케이션 artifact를 사용하지만 런타임 역할은 다릅니다.

- **Writer 인스턴스:** 조회와 변경 모두 수행
- **Reader 인스턴스:** 조회만 수행하고 DB 상태를 변경하지 않음

각 서버는 동시에 Writer를 하나만 가집니다. Komit의 Writer와 Escrow의 Writer는 서로 독립적으로 선출·전환됩니다.

```text
Client / Frontend
        │
        ├── Komit Load Balancer
        │       ├── READ  ── Komit Writer + Komit Reader
        │       └── WRITE ── Komit Writer only
        │                       └── Komit가 소유한 공용 외부 저장소
        │
        └── Escrow Load Balancer
                ├── READ  ── Escrow Writer + Escrow Reader
                └── WRITE ── Escrow Writer only
                                └── Escrow가 소유한 공용 외부 저장소
```

여기서 이중화는 애플리케이션 서버 인스턴스를 의미합니다. 데이터베이스, Redis, 메시지 브로커의 고가용성 구성은 별도 인프라 설계로 정하며 이 문서가 자동으로 DB 이중화를 전제하지 않습니다.

### 6.1 읽기·쓰기 라우팅

- 조회 요청은 Writer와 Reader 모두 처리할 수 있습니다.
- 생성·수정·삭제·상태 전이 요청은 Writer만 처리합니다.
- 로드밸런서는 read route를 두 인스턴스에 분산하고 write route를 현재 Writer에만 전달합니다.
- 단순히 HTTP method만 보지 않고 API 명세에 정의된 실제 동작을 기준으로 read/write를 분류합니다. 조회 성격의 POST와 상태를 변경하는 비표준 endpoint가 있다면 route 설정에 명시합니다.
- Reader로 write 요청이 전달되지 않는 것이 1차 방어선입니다. 애플리케이션과 DB 권한에서도 write를 다시 차단합니다.
- 인스턴스 역할은 `local`·`prod` 프로필과 분리된 배포 환경 값으로 주입합니다. 예: `APP_INSTANCE_ROLE=WRITER|READER`.
- 애플리케이션 시작 시 역할 값이 없거나 유효하지 않으면 시작을 실패시킵니다.
- Reader가 받은 write 요청을 자신이 처리하거나 임의로 Writer에 HTTP redirect하지 않습니다.
- Writer 부재 중 write API를 외부에 실패 응답으로 노출해야 한다면 상태 코드와 서버별 응답 body를 먼저 목 Swagger에 정의합니다. 운영 편의를 이유로 계약에 없는 응답을 즉석에서 만들지 않습니다.

### 6.2 DB 권한과 트랜잭션 방어

- Writer는 읽기·쓰기 DB 계정을 사용하고 Reader는 DB에서 실제로 쓰기가 제한된 read-only 계정을 사용합니다.
- Reader의 DB 계정에는 `INSERT`, `UPDATE`, `DELETE`, DDL 권한을 부여하지 않습니다.
- `@Transactional(readOnly = true)`는 성능과 의도 표현을 위한 규칙이며 보안 경계로 신뢰하지 않습니다. 최종 쓰기 차단은 DB 권한으로 보장합니다.
- write Service는 Writer 역할에서만 실행되도록 공통 guard를 적용합니다.
- Flyway migration은 Reader가 실행하지 않습니다. 배포 파이프라인의 단일 migration 단계 또는 명시적으로 지정된 Writer 측에서만 수행합니다.
- Reader가 실수로 write를 시도하면 정상 처리로 숨기지 않고 즉시 실패시키며 운영 경보를 발생시킵니다.

### 6.3 Writer 장애 전환과 split-brain 방지

Writer 장애 시 Reader를 새 Writer로 승격할 수 있지만, 두 인스턴스가 동시에 write 가능한 상태가 되어서는 안 됩니다.

1. 기존 Writer를 write 대상에서 제거하고 쓰기 권한을 fencing합니다.
2. 기존 Writer가 더 이상 write할 수 없음을 확인합니다.
3. Reader에 write DB 권한과 Writer 역할을 부여합니다.
4. 로드밸런서 write route를 새 Writer로 전환합니다.
5. health check, write smoke test, 중복 처리 여부를 확인합니다.

- 역할 변경은 사람의 기억이나 단순 로컬 flag가 아니라 배포 설정, lease 또는 외부 조정 저장소로 관리합니다.
- 네트워크 단절된 이전 Writer가 살아 돌아와도 write하지 못하도록 DB 권한, lease/epoch 또는 이에 준하는 fencing 수단을 둡니다.
- 자동 failover를 도입하더라도 동시에 한 Writer만 허용한다는 불변조건을 테스트합니다.
- 장애 전환 중 재시도된 계약·결제·정산 요청은 idempotency key와 DB 제약조건으로 중복 처리를 막습니다.

### 6.4 무상태 애플리케이션

- 조회 요청은 어느 인스턴스가 받아도 같은 계약과 권한 규칙으로 처리되어야 합니다.
- 로그인 세션, refresh token, 작업 상태를 JVM 메모리에만 저장하지 않습니다.
- 인증은 검증 가능한 token을 사용하거나, 서버 상태가 필요하면 모든 인스턴스가 접근하는 공용 저장소를 사용합니다.
- refresh token은 Redis에 저장하고 TTL로 만료를 관리합니다. 재발급 시 이전 값을 덮어쓰고, 로그아웃 시 삭제합니다. DB 테이블에는 저장하지 않습니다.
- 업로드 파일과 생성 결과물을 인스턴스 로컬 디스크에 영구 보관하지 않습니다. 공용 Object Storage를 사용합니다.
- 로컬 메모리 캐시는 없어져도 정합성이 깨지지 않는 값에만 사용합니다.
- sticky session은 성능 최적화로 사용할 수 있지만 정확성을 보장하는 필수 조건으로 삼지 않습니다.
- 채팅·WebSocket 연결 상태를 여러 인스턴스가 공유해야 하면 Redis Pub/Sub 또는 메시지 브로커 같은 공용 전달 계층을 사용합니다. Reader에 연결된 사용자가 상태 변경 명령을 보내는 경우 Reader가 DB에 직접 쓰지 않고 Writer가 소비하는 command channel로 전달합니다.

### 6.5 중복 요청과 동시 실행

로드밸런서 재시도, 클라이언트 재시도 또는 네트워크 timeout으로 같은 명령이 Writer에 다시 실행될 수 있습니다.

- 계약 수락, 상담 신청, 결제·환불·정산 요청은 멱등하게 설계합니다.
- 외부 결제 요청처럼 중복 실행 비용이 큰 API는 idempotency key를 사용합니다.
- DB unique constraint와 상태 조건부 갱신을 최종 중복 방어선으로 둡니다.
- 동시 상태 변경은 `@Version` 또는 원자적 조건부 update로 보호하고 충돌은 409로 변환합니다.
- 요청이 timeout 되었더라도 실패로 단정하지 말고 식별자로 처리 상태를 재조회할 수 있게 합니다.

### 6.6 스케줄러와 비동기 작업

- 상태를 변경하는 `@Scheduled` 작업과 consumer는 Writer에서만 활성화합니다.
- Reader에서는 조회·모니터링처럼 DB 상태를 변경하지 않는 작업만 실행할 수 있습니다.
- Writer 전환 과정에서 작업이 다시 실행될 수 있으므로 스케줄러와 consumer도 멱등하게 구현합니다.
- 한 번만 실행되어야 하는 작업은 Writer 역할 제한과 함께 외부 스케줄러, 작업 큐 또는 분산 lock을 사용합니다.
- JVM의 `synchronized`나 로컬 lock으로 인스턴스 간 중복 실행을 막을 수 없습니다.
- 메시지 consumer는 동일 메시지가 다시 전달되어도 안전하도록 멱등하게 처리합니다.
- 실패 작업은 재시도 횟수, backoff, 최종 실패 보관 및 수동 재처리 절차를 둡니다.

### 6.7 헬스체크와 무중단 배포

- 로드밸런서는 Actuator 기반 liveness와 readiness를 구분해 확인합니다.
- read readiness는 Writer와 Reader 모두 제공하고, write readiness는 현재 Writer만 통과해야 합니다.
- 로드밸런서의 read target group에는 두 인스턴스를, write target group에는 write readiness를 통과한 Writer 하나만 등록합니다.
- readiness 실패 인스턴스에는 해당 종류의 신규 트래픽을 보내지 않습니다.
- 종료 시 신규 요청 수신을 먼저 중단하고 진행 중 요청을 마칠 수 있도록 graceful shutdown을 사용합니다.
- 모든 인스턴스가 같은 버전으로 한 번에 교체된다고 가정하지 않습니다. API와 DB migration은 롤링 배포 중 구버전·신버전이 잠시 공존해도 호환되어야 합니다.
- 파괴적인 컬럼 삭제·이름 변경은 추가 → 양쪽 버전 전환 → 제거 순서의 expand/contract 방식으로 진행합니다.
- Flyway migration은 배포 파이프라인의 통제된 한 단계에서 실행하는 것을 원칙으로 하고, 여러 인스턴스가 동시에 위험한 DDL을 수행하게 두지 않습니다.

### 6.8 서버 간 호출

- Komit에서 Escrow로 보내는 모든 쓰기 요청에는 추적 식별자와 멱등성 기준을 둡니다.
- 네트워크 오류와 5xx만 제한적으로 재시도하며, 검증·권한·상태 충돌 오류는 자동 재시도하지 않습니다.
- 재시도는 지수 backoff와 최대 횟수를 설정합니다.
- 연쇄 장애가 예상되는 동기 호출에는 circuit breaker 또는 명시적인 기능 저하 전략을 검토합니다.
- 서버 간 요청에도 사용자 token을 무조건 전달하지 말고, 필요한 사용자 맥락과 서버 간 인증 방식을 분리해 설계합니다.
- 양쪽 로그는 같은 `traceId`/상관관계 ID로 추적할 수 있어야 합니다.

## 7. Komit DDD 패키지 구조

기능을 전체 `controller`, 전체 `service`처럼 계층부터 나누지 않습니다. 최상위는 비즈니스 도메인으로 나누고, 각 도메인 안에서 필요한 계층만 만듭니다.

```text
com.a501.backend
├── BackendApplication.java
├── common/
│   ├── exception/          # GlobalExceptionHandler, ErrorResponse, ErrorCode
│   ├── response/           # 공통 목록 응답 등
│   ├── security/           # 인증 사용자 표현, 공통 보안 지원 코드
│   └── util/               # 둘 이상의 도메인이 실제로 쓰는 범용 코드
├── config/                 # SecurityConfig, OpenApiConfig, JpaConfig 등
└── domain/
    ├── auth/               # 로그인, 토큰 발급·재발급, 로그아웃
    ├── user/               # 계정과 공통 사용자 정보
    ├── developer/          # 개발자 프로필, 기술, 요금, 경력, 포트폴리오, 리뷰 조회
    ├── project/            # 의뢰 프로젝트와 요청서
    ├── diagnosis/          # 문제 진단 및 AI 진단 결과
    ├── matching/           # 개발자 검색, 추천, 상담 대상 선택·신청
    ├── conversation/       # 상담/개발 채팅방과 메시지
    ├── contract/           # 상담·개발 계약, 계약 버전과 동의
    ├── consulting/         # 유료 상담 세션과 상태
    ├── development/        # 개발 작업 진행, 결과물, 수정 요청
    ├── settlement/         # Komit 측 결제·정산 조회와 상태
    ├── dispute/            # 이의 제기와 분쟁 접수
    ├── review/             # 계약 완료 후 평가와 리뷰
    ├── notification/       # 알림 생성·조회·읽음 처리
    └── file/               # 업로드 메타데이터와 파일 접근 정책
```

패키지는 실제 기능을 구현할 때 생성합니다. 비어 있는 패키지를 전체 목록대로 미리 만들지 않습니다. 도메인 이름은 단수형 소문자를 사용합니다.

각 도메인의 내부 기본 구조는 다음과 같습니다.

```text
domain/developer/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
│   ├── request/
│   └── response/
└── exception/              # 도메인 전용 타입이 꼭 필요할 때만 생성
```

복잡도가 커지면 도메인 안에서 `application`, `domain`, `infrastructure`, `presentation`으로 발전시킬 수 있습니다. 단, 팀 전체 합의 없이 일부 도메인만 다른 아키텍처로 바꾸지 않습니다.

### 7.1 도메인 간 의존 규칙

- 다른 도메인의 `entity`와 `repository`를 직접 import하지 않습니다.
- 다른 도메인의 데이터나 행위가 필요하면 해당 도메인의 Service를 호출합니다.
- 도메인 사이에는 Entity 대신 ID, 불변 DTO 또는 명시적인 결과 객체를 전달합니다.
- 순환 의존이 생기면 한쪽 Service를 억지로 주입하지 말고 유스케이스 조정 서비스 또는 이벤트 도입을 검토합니다.
- 트랜잭션 하나에서 여러 도메인을 함께 변경해야 하면 변경의 소유권과 실패 시 원자성을 먼저 합의합니다.
- `common`은 아무 코드나 모으는 곳이 아닙니다. 한 도메인에서만 사용하는 코드는 해당 도메인에 둡니다.

## 8. 네이밍 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 클래스 | `PascalCase` | `DeveloperProfileService` |
| 변수·메서드 | `camelCase` | `findByDeveloperId` |
| 상수 | `UPPER_SNAKE_CASE` | `MAX_PAGE_SIZE` |
| Java 패키지 | 소문자 단수형 | `domain.developer` |
| DB 테이블·컬럼 | `snake_case` | `developer_profile`, `created_at` |
| JSON 필드 | `camelCase` | `developerId`, `createdAt` |
| API 경로 | `kebab-case`, 복수 리소스 | `/api/developer-profiles` |
| 환경 변수 | `UPPER_SNAKE_CASE` | `DB_URL`, `JWT_SECRET` |
| Flyway 파일 | `V{번호}__{설명}.sql` | `V3__create_contract_table.sql` |

역할이 클래스명에 드러나야 합니다.

```text
DeveloperController
DeveloperService
DeveloperRepository
Developer                 # Entity는 불필요한 Entity 접미사를 붙이지 않음
CreateDeveloperRequest
DeveloperDetailResponse
DeveloperErrorCode
```

약어도 일반 단어처럼 표기합니다. `APIController`, `DTOMapper`보다 `ApiController`, `DtoMapper`를 사용합니다. 기본 패키지는 Java 관례에 따라 `com.a501.backend`처럼 모두 소문자로 작성합니다.

## 9. 계층별 책임

### Controller

- HTTP 요청을 DTO로 받고 `@Valid`로 검증합니다.
- 인증 사용자와 path/query/body 값을 Service에 전달합니다.
- HTTP 상태와 응답 DTO를 반환합니다.
- 비즈니스 판단, JPA Entity 변경, Repository 호출을 하지 않습니다.
- `@Tag`, `@Operation`으로 실제 구현 문서를 작성합니다.

### Service

- 유스케이스, 권한 확인, 상태 전이와 트랜잭션 경계를 담당합니다.
- 클래스에 `@Transactional(readOnly = true)`를 적용합니다.
- 데이터를 변경하는 메서드에만 `@Transactional`을 다시 적용합니다.
- Controller 전용 객체인 `HttpServletRequest`, `ResponseEntity`에 의존하지 않습니다.
- 구현체가 하나라면 인터페이스를 만들지 않고 `@Service` 클래스를 직접 사용합니다.

### Repository

- 기본적으로 `JpaRepository<Entity, Id>`를 상속하는 인터페이스로 둡니다.
- 복잡한 조회는 쿼리 메서드, `@EntityGraph`, JPQL `@Query` 순서로 해결합니다.
- 위 방법으로 표현할 수 없는 동적 조건, 복합 검색, 통계성 조회는 QueryDSL로 작성합니다. 기준은 13.3입니다.
- Controller가 Repository를 직접 호출하지 않습니다.

### Entity

- JPA 매핑과 도메인 상태 변경 규칙을 가집니다.
- 무분별한 `setter`를 금지합니다.
- 상태 변경은 `acceptContract()`, `completeConsulting()`처럼 의도가 드러나는 메서드로 표현합니다.
- 외부 응답으로 직접 반환하지 않습니다.

### DTO

- 외부 계약과 내부 Entity 사이의 경계입니다.
- 단순한 불변 DTO는 Java `record`를 우선 사용합니다.
- 요청은 `dto/request`, 응답은 `dto/response`에 둡니다.
- 변환은 `Response.from(entity)` 또는 별도 Mapper 한 곳에서 담당합니다.
- Entity를 그대로 포함하지 않습니다.

## 10. DTO와 입력 검증

```java
public record CreateConsultationRequest(
        @NotNull Long developerId,
        @NotNull Long requestId,
        @Size(max = 1000) String message
) {}

public record DeveloperSummaryResponse(
        Long developerId,
        String name,
        String introduction
) {
    public static DeveloperSummaryResponse from(Developer developer) {
        return new DeveloperSummaryResponse(
                developer.getId(),
                developer.getName(),
                developer.getIntroduction()
        );
    }
}
```

- Controller의 body DTO에는 반드시 `@Valid`를 붙입니다.
- 필수 여부, 문자열 길이, 숫자 범위는 Bean Validation으로 검증합니다.
- 데이터 형식 검증 실패와 이미 유효한 값을 이용한 비즈니스 규칙 위반을 구분해 처리합니다. 각 서버가 반환할 상태 코드와 응답 코드는 목 명세를 따릅니다.
- 검증 에러에는 사용자가 입력한 실제 값을 되돌려주지 않습니다.

## 11. API 규격과 mock 서버

요청과 응답의 구체적인 규격은 이 문서가 정하지 않습니다. **`mock` 서버 Swagger가 단일 기준입니다.** 5.2의 원칙을 그대로 따릅니다.

- Komit 기준: [`mock/app/routers/service.py`](../mock/app/routers/service.py), Swagger `/service/docs`
- Escrow 기준: [`mock/app/routers/escrow.py`](../mock/app/routers/escrow.py), Swagger `/escrow/docs`

목 Swagger에서 확인하고 그대로 구현할 항목입니다.

- HTTP method와 `/api` 이후 경로
- 요청 body, query·path parameter, 인증 정보를 싣는 위치
- 성공 응답 body와 HTTP 상태 코드
- 에러 응답 body, HTTP 상태 코드, 응답 코드 목록
- 페이지네이션과 정렬 parameter 형식

Komit와 Escrow는 요청·응답 형식이 서로 다릅니다. Komit는 리소스 직접 반환과 `{code, message}` 에러 형식을, Escrow는 SSAFY 금융망의 `Header`/`REC` 형식을 사용합니다. 한 서버의 형식을 다른 서버에 적용하지 않으며, 같아야 하는 것은 두 서버의 모양이 아니라 각 서버 내부의 일관성입니다.

명세에 없는 필드가 필요하면 코드부터 고치지 않습니다. ERD와 목 명세를 먼저 수정하고 합의한 뒤 구현합니다. 실제 응답이 목 명세와 다르면 어느 쪽이 합의된 계약인지 확인하고 맞추며, 프런트엔드가 보정하도록 두지 않습니다.

### 11.1 목 명세가 다루지 않는 구현 규칙

- Content-Type은 `application/json`을 사용합니다.
- 명세에 없는 필드를 응답에 임의로 추가하지 않습니다.
- ERD 필드는 단어를 바꾸지 않고 `snake_case`에서 `camelCase`로 표기만 변환합니다.
- 금액은 원 단위 정수로 다루고 Java `long`/`Long`을 사용합니다. 소수 통화가 실제 요구사항에 추가될 때만 `BigDecimal`을 검토합니다.
- 일시는 서버 내부에서 `Instant` 또는 `OffsetDateTime`으로 다루고 API 경계에서 명확한 offset을 제공합니다.
- Komit의 데이터 형식 검증 실패와 비즈니스 규칙 위반은 서로 다른 상태 코드로 구분합니다. 구체적인 코드는 목 명세를 따릅니다.
- 로그에 token, API key, user key, 비밀번호, 계좌번호 전체를 남기지 않습니다.

## 12. 예외 처리

두 서버는 예외를 같은 JSON으로 변환하지 않습니다. 예외 클래스와 내부 처리 방식은 Spring 공통 패턴을 사용할 수 있지만, 최종 HTTP 상태와 body는 각 서버의 목 기준 API와 같아야 합니다.

### 12.1 Komit 예외 처리

공통 예외는 `common/exception`에서 관리합니다.

```text
common/exception/
├── BusinessException.java
├── ErrorCode.java
├── ErrorResponse.java
└── GlobalExceptionHandler.java
```

도메인별 에러 코드는 해당 도메인에 둡니다.

```java
public enum ContractErrorCode implements ErrorCode {
    CONTRACT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CONTRACT_NOT_FOUND",
            "계약을 찾을 수 없습니다."
    ),
    CONTRACT_ALREADY_ACCEPTED(
            HttpStatus.CONFLICT,
            "CONTRACT_ALREADY_ACCEPTED",
            "이미 수락된 계약입니다."
    );
}
```

도메인마다 내용이 거의 같은 Exception 클래스를 반복 생성하지 않습니다. 기본은 `ErrorCode`와 공통 `BusinessException` 조합이며, 별도의 타입이 실제 처리 흐름을 바꿀 때만 도메인 예외 클래스를 만듭니다.

Komit 에러 응답은 노출 범위를 통제합니다.

- `message`에는 사용자에게 그대로 보여줄 수 있는 문장만 담습니다.
- 스택 트레이스, 예외 클래스, SQL, 테이블·컬럼, 파일 경로, 내부 서비스명·호스트·포트, 라이브러리 버전, 설정값을 응답에 넣지 않습니다.
- 500은 고정된 안전한 문구를 사용하고 실제 원인은 서버 로그에서 확인합니다. 문의 추적이 필요하면 의미 없는 임의 식별자인 `traceId`만 추가합니다.
- 로그인과 비밀번호 재설정에서는 계정 존재 여부를 구분해 알려주지 않습니다.
- 다른 사용자의 요청서, 계약, 정산, 채팅처럼 존재 자체가 민감한 리소스는 접근 권한이 없어도 404를 반환합니다. 공개 프로필과 공개 목록처럼 존재가 이미 공개된 리소스만 403을 사용합니다. 리소스의 당사자(계약의 의뢰인·개발자 등)가 자기 역할에 맞지 않는 동작을 요청한 경우는 존재가 이미 알려져 있으므로 403을 사용합니다.
- 검증 에러 목록에는 필드명과 사유만 넣고 사용자가 입력한 실제 값을 넣지 않습니다.
- 에러 코드는 `USER_TABLE_FK_VIOLATION` 같은 구현 용어가 아니라 `CONTRACT_ALREADY_ACCEPTED` 같은 도메인 용어를 사용합니다.


### 12.2 Escrow 예외 처리

- Escrow 전역 예외 처리기는 실패 응답을 목 명세의 `Header` 형태로 생성합니다. 응답 코드와 HTTP 상태의 대응은 목 Swagger를 따릅니다.
- Bean Validation 및 JSON parsing 오류도 기본 Spring 400 body로 내보내지 않고 목 명세가 정한 응답 코드로 변환합니다.
- 실패 응답에 `REC`, stack trace, 예외 클래스명 또는 내부 구현 정보를 넣지 않습니다.
- `apiKey`와 `userKey`는 성공·실패 응답에 포함하지 않습니다.
- `Header`와 `REC`의 대소문자를 바꾸거나 Komit 응답 wrapper와 혼합하지 않습니다.
- Komit의 `ErrorResponse`, `INVALID_REQUEST`, `errors[]` 구조를 재사용하지 않습니다.
- Java 내부에서 이름이 같은 `ErrorCode` interface를 사용하더라도 Escrow 응답 DTO와 변환기는 Escrow 서버 안에 독립적으로 둡니다.
- 기준 코드 목록에 없는 응답 코드를 임의로 만들지 않습니다. 필요하면 목 명세에 먼저 추가합니다.
- 실제 Spring 서버의 전역 예외 처리기는 FastAPI 목 서버와 동일한 HTTP 상태, 응답 코드와 JSON 구조를 생성해야 합니다.

## 13. PostgreSQL과 JPA 규칙

### 13.1 개발 환경 일치

- local과 prod 모두 PostgreSQL을 사용합니다.
- 로컬은 Docker Compose의 PostgreSQL 18.6을 사용합니다.
- 통합 테스트는 별도의 `test` 프로필 파일 없이 Testcontainers PostgreSQL 연결 정보를 테스트 코드에서 주입합니다.
- H2는 PostgreSQL과 SQL 문법, 타입, 제약조건, 정렬 동작이 달라 사용하지 않습니다.
- 단위 테스트는 DB 없이 실행 가능하도록 도메인 로직을 분리합니다.

### 13.2 Entity 기본

```java
@Getter
@Entity
@Table(name = "contract")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContractStatus status;

    public void accept() {
        if (status != ContractStatus.PROPOSED) {
            throw new BusinessException(ContractErrorCode.CONTRACT_STATE_CONFLICT);
        }
        status = ContractStatus.ACCEPTED;
    }
}
```

- `@Setter`를 사용하지 않습니다.
- 기본 생성자는 `PROTECTED`로 제한합니다.
- 연관관계는 기본적으로 지연 로딩(`LAZY`)을 사용합니다.
- 상태 충돌 가능성이 있는 계약·정산·분쟁에는 낙관적 잠금 `@Version`을 검토합니다.
- Enum은 `EnumType.STRING`으로 저장합니다.
- 양방향 연관관계는 꼭 필요한 경우에만 사용합니다.
- 컬렉션과 연관 Entity를 JSON으로 직접 직렬화하지 않습니다.
- N+1 가능성이 있는 조회는 fetch join 또는 `@EntityGraph`로 명시적으로 해결하고 쿼리 수를 확인합니다.

### 13.3 QueryDSL 사용 기준

바닐라 JPA로 표현할 수 없는 조회는 **QueryDSL**로 작성합니다. 네이티브 SQL 문자열이나 조건마다 분기하는 JPQL 문자열 조립으로 해결하지 않습니다.

QueryDSL을 사용하는 경우는 다음과 같습니다.

- 검색 필터처럼 조건 개수가 요청에 따라 달라지는 동적 조회
- 여러 Entity를 조인해 화면 단위 데이터를 한 번에 만드는 조회
- 집계, 그룹핑, 서브쿼리가 필요한 통계성 조회
- 정렬 기준과 페이지 조건이 조합으로 바뀌는 목록 조회

다음은 QueryDSL로 바꾸지 않습니다. 단순 조회는 Spring Data JPA 쿼리 메서드로 충분합니다.

- 단건 조회와 단순 조건 조회
- `@EntityGraph`나 fetch join으로 해결되는 N+1 문제

구조는 다음과 같이 둡니다. QueryDSL 구현체는 해당 도메인의 `repository` 안에 두고 다른 도메인에서 참조하지 않습니다.

```text
domain/developer/repository/
├── DeveloperRepository.java              # JpaRepository + DeveloperRepositoryCustom
├── DeveloperRepositoryCustom.java        # QueryDSL 조회 메서드 선언
└── DeveloperRepositoryImpl.java          # JPAQueryFactory 구현
```

#### 성능 규칙

QueryDSL 쿼리는 필요한 정보만 가져오도록 작성합니다.

- **필요한 컬럼만 조회합니다.** Entity 전체를 가져와 DTO로 변환하지 말고 `Projections` 또는 `@QueryProjection`으로 조회 전용 DTO에 직접 담습니다.
- 화면에 쓰지 않는 연관 Entity를 조인하거나 가져오지 않습니다.
- 컬렉션 조인으로 행이 늘어나는 조회는 `distinct`로 덮지 말고 조회를 분리하거나 `batch size`를 사용합니다.
- 목록 조회는 반드시 `limit`과 `offset`을 적용합니다. 전체 결과를 메모리에 올린 뒤 자르지 않습니다.
- count 쿼리는 필요할 때만 실행하고, 정렬과 불필요한 조인을 제거한 형태로 분리해 작성합니다.
- 조건이 없는 필터는 `null`을 반환하는 `BooleanExpression` 메서드로 표현해 `where` 절에서 빠지게 합니다. 항상 참인 조건을 넣지 않습니다.
- 조회 결과를 애플리케이션에서 반복문으로 다시 필터링하거나 집계하지 않습니다. DB에서 끝냅니다.
- 인덱스를 타지 않는 조건(선행 와일드카드 `LIKE`, 컬럼 가공)을 피하고, 필요하면 인덱스를 migration에 추가합니다.
- 반복 실행되는 조회는 `EXPLAIN`으로 실행 계획과 쿼리 수를 확인하고 결과를 PR에 적습니다.

`JPAQueryFactory`는 `config`에서 빈으로 등록해 주입받아 사용하고, 구현체마다 새로 생성하지 않습니다. 생성된 Q 클래스는 빌드 산출물이므로 커밋하지 않습니다.

### 13.4 스키마 변경

- 운영 스키마는 Flyway로만 변경합니다.
- `spring.jpa.hibernate.ddl-auto`는 local/prod에서 `validate`를 사용합니다.
- `update`, `create`, `create-drop`을 운영 설정에 사용하지 않습니다.
- 이미 공유된 migration 파일은 수정하지 않고 새 버전 파일을 추가합니다.
- 한 migration에는 하나의 설명 가능한 변경 단위를 담습니다.
- 테이블·컬럼 변경은 ERD에도 함께 반영합니다.

```text
src/main/resources/db/migration/
├── V1__create_user_tables.sql
├── V2__create_developer_tables.sql
└── V3__create_contract_tables.sql
```

PostgreSQL의 `jsonb`, `timestamptz`, partial index 같은 기능은 실제 조회 요구가 있을 때 사용합니다. 특정 DB 기능 도입은 Repository 테스트로 동작을 검증하고 문서화합니다.

## 14. 설정과 프로필

```text
src/main/resources/
├── application.yml
├── application-local.yml
└── application-prod.yml
```

- 공통 설정만 `application.yml`에 둡니다.
- 활성 프로필은 `local`, `prod` 두 개만 사용합니다.
- Writer/Reader는 Spring profile이 아닙니다. `local-writer`, `prod-reader` 같은 조합 프로필을 만들지 않고 `APP_INSTANCE_ROLE` 같은 별도 배포 설정으로 구분합니다.
- local은 로컬 PostgreSQL을 사용하고 prod는 환경 변수로 운영 PostgreSQL과 외부 인프라에 연결합니다.
- 테스트는 `application-test.yml`을 만들지 않습니다. Testcontainers의 `@ServiceConnection` 또는 동적 property 주입으로 격리된 PostgreSQL에 연결합니다.
- 비밀번호, JWT secret, API key, access token을 설정 파일이나 Git에 커밋하지 않습니다. 운영에 쓰지 않는 개발용 예시 값은 예외입니다. `docs/jwt-samples.md`의 샘플 서명 키처럼 값에 `dev-only`를 명시하고 운영 값으로 쓰지 않습니다.
- `.env`는 예시 파일만 공유하고 실제 값은 개인 환경 또는 CI/CD secret으로 관리합니다.
- 환경 변수 누락 시 안전하지 않은 기본값으로 실행하지 말고 애플리케이션 시작을 실패시킵니다.

공통 JPA 권장 설정:

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```

## 15. 보안 규칙

- 각 서버는 자신의 Spring Security 설정을 독립적으로 관리합니다.
- Komit 사용자 API는 `Authorization: Bearer <accessToken>`을 기준으로 인증합니다.
- Escrow 금융망 API는 JSON `Header`의 `apiKey`와 `userKey` 및 서버 간 인증 정책을 기준으로 검증합니다. Komit의 사용자 인증 설정을 그대로 공유하지 않습니다.
- 사용자 `userKey`와 개인 계좌번호는 Komit Service가 암호화해 보관하고, Escrow는 금융망 호출이 끝난 뒤 요청 범위를 넘어 보관하지 않습니다.
- 공개 엔드포인트를 명시하고 나머지는 기본적으로 인증을 요구합니다.
- Controller에서 전달된 사용자 ID를 신뢰하지 않고 인증 컨텍스트의 사용자와 소유권을 대조합니다.
- 비밀번호는 검증된 password encoder로 단방향 해시합니다.
- access token, refresh token, 비밀번호, 주민번호, 계좌번호 전체 값을 로그에 남기지 않습니다.
- 파일 다운로드는 파일 식별자뿐 아니라 연결된 프로젝트·계약의 접근 권한도 확인합니다.
- CORS 허용 origin은 환경별 명시 목록으로 관리하며 `*`와 credential을 함께 사용하지 않습니다.
- 인증 실패 문구와 처리 시간 차이로 계정 존재 여부가 드러나지 않게 합니다.

## 16. 트랜잭션과 동시성

- 트랜잭션 경계는 Service에 둡니다.
- 외부 API 호출을 DB 트랜잭션 안에서 오래 유지하지 않습니다.
- 계약 수락, 결제 요청, 정산 확정처럼 재시도될 수 있는 명령은 멱등성을 고려합니다.
- 같은 요청의 중복 실행은 unique constraint, 상태 검사 또는 idempotency key로 차단합니다.
- 동시 변경이 가능한 리소스는 낙관적 잠금 충돌을 409로 변환합니다.
- 이벤트 발행이 DB commit과 함께 보장되어야 하면 Outbox 패턴 도입을 검토합니다.
- Writer 역할은 장애 전환으로 다른 인스턴스로 이동할 수 있으므로 JVM 내부 lock을 정합성 수단으로 사용하지 않습니다.

## 17. API 문서화 규칙

### 17.1 목 서버 Swagger

Komit은 `/service/docs`, Escrow는 `/escrow/docs`를 사용합니다. 응답 스키마를 작성할 때는 같은 앱의 default API만 참고하고 다른 앱의 default API를 복사하지 않습니다.

목 서버 API 설명에는 다음을 포함합니다.

- API가 수행하는 사용자 동작
- 필요한 인증과 권한
- request/path/query 필드 의미와 제약
- 성공 응답 예시
- 발생 가능한 주요 상태 코드와 에러 코드

테이블명, SQL, 내부 클래스, 서버 경로 같은 구현 상세는 설명에 넣지 않습니다. Komit 라우터는 Komit 서비스 등록 목록에, Escrow 라우터는 Escrow 등록 목록에만 연결합니다.

### 17.2 실제 Spring API

```java
@Tag(name = "Developer", description = "개발자 검색 및 프로필 API")
@RestController
@RequestMapping("/api/developers")
@RequiredArgsConstructor
public class DeveloperController {

    @Operation(summary = "개발자 상세 조회")
    @GetMapping("/{developerId}")
    public DeveloperDetailResponse getDeveloper(
            @PathVariable Long developerId
    ) {
        return developerService.getDeveloper(developerId);
    }
}
```

- Controller에 `@Tag`, 각 엔드포인트에 `@Operation`을 작성합니다.
- 실제 springdoc의 schema와 예시는 목 Swagger와 같아야 합니다.
- API 변경 PR에는 목 명세 변경과 실제 구현 변경을 함께 연결합니다.
- 불일치는 실제 서버 구현에 맞춰 프런트엔드를 임의 수정하지 말고, 먼저 합의된 계약이 무엇인지 확인합니다.

## 18. 로깅과 관측성

- 로깅은 Lombok `@Slf4j`와 parameterized logging을 사용합니다.
- 정상 흐름을 `error`로 기록하지 않습니다.
- 예외는 전역 처리기에서 한 번만 기록하고 같은 stack trace를 여러 계층에서 중복 출력하지 않습니다.
- 요청 추적용 `traceId`를 로그 컨텍스트에 넣고, 500 응답에도 같은 값을 선택적으로 제공합니다.
- `instanceId`, 애플리케이션명, 배포 버전을 로그 필드로 남겨 이중화 환경에서 어느 인스턴스가 처리했는지 확인할 수 있게 합니다.
- 운영 로그에 요청·응답 body 전체를 기본 출력하지 않습니다.
- Actuator health endpoint는 인프라 확인용으로 사용하되 민감한 상세 정보는 외부에 공개하지 않습니다.

```java
log.info("Contract accepted. contractId={}, actorId={}", contractId, actorId);
```

문자열 연결 방식인 `log.info("id=" + id)`는 사용하지 않습니다.

## 19. 테스트 규칙

### 단위 테스트

- 도메인 상태 전이와 비즈니스 규칙을 빠르게 검증합니다.
- 외부 시스템이나 DB 없이 실행합니다.
- 정상 사례뿐 아니라 경계값과 실패 사례를 함께 작성합니다.

### Repository 통합 테스트

- Testcontainers PostgreSQL 18 계열을 사용합니다.
- unique constraint, enum, timestamp, fetch join 등 PostgreSQL 실제 동작을 검증합니다.
- H2 기반 테스트로 대체하지 않습니다.

### Controller와 계약 테스트

- `MockMvc`로 method, URL, 인증, validation, 상태 코드와 JSON schema를 검증합니다.
- 각 서버의 성공·실패 응답이 해당 서버의 목 Swagger와 같은 상태 코드, 응답 코드, JSON 구조인지 확인합니다.
- Escrow는 실패 응답에 `REC`가 포함되지 않는지 확인합니다.
- 타인의 민감한 리소스 접근이 404로 가려지는지 확인합니다.

### 이중화 역할 테스트

- 같은 조회 API가 Writer와 Reader 역할에서 모두 성공하는지 확인합니다.
- Reader 역할에서 모든 write Service와 상태 변경 scheduler가 차단되는지 확인합니다.
- Reader DB 계정으로 `INSERT`, `UPDATE`, `DELETE`, DDL이 실패하는지 통합 테스트 또는 배포 검증으로 확인합니다.
- Writer 장애 전환 시 두 Writer가 동시에 활성화되지 않는지 확인합니다.
- 장애 전환 전후에 같은 idempotency key를 다시 사용해도 상태가 한 번만 변경되는지 확인합니다.

### 실행 기준

```shell
./gradlew test
./gradlew build
```

Windows에서는 각각 `gradlew.bat test`, `gradlew.bat build`를 사용합니다. PR을 열기 전에 최소한 변경 도메인의 테스트와 전체 build를 통과시킵니다.

## 20. 협업과 Git 규칙

브랜치와 커밋 형식은 저장소의 [`docs/git_guide.md`](./git_guide.md)를 따릅니다.

- `main`, `develop`에 직접 push하지 않습니다.
- 기능은 이슈 단위 브랜치와 PR로 병합합니다.
- PR 하나는 가능한 한 하나의 도메인 또는 하나의 유스케이스를 다룹니다.
- DDL과 Entity, 목 API와 실제 API처럼 함께 바뀌어야 하는 파일을 빠뜨리지 않습니다.
- 다른 도메인의 내부 구조를 바꾸는 경우 해당 도메인 담당자의 리뷰를 요청합니다.
- 자동 생성 파일, IDE 개인 설정, secret을 커밋하지 않습니다.

PR 설명에는 다음을 포함합니다.

- 변경한 사용자 동작과 도메인
- API 계약 변경 여부와 목 Swagger 경로
- ERD 또는 Flyway 변경 여부
- 인증·인가 영향
- 테스트한 항목과 결과
- SonarQube Quality Gate 결과와 남겨 둔 이슈
- 배포 또는 환경 변수 변경 사항

## 21. 코드 리뷰 체크리스트

- [ ] 패키지가 계층이 아니라 도메인부터 나뉘어 있는가?
- [ ] 다른 도메인의 Entity/Repository를 직접 참조하지 않는가?
- [ ] Controller에 비즈니스 로직이 없는가?
- [ ] 쓰기 Service에만 쓰기 트랜잭션이 적용되었는가?
- [ ] Entity를 API 응답으로 직접 반환하지 않는가?
- [ ] API가 목 Swagger의 method, path, field, status와 일치하는가?
- [ ] Komit과 Escrow의 요청·응답·검증 오류 형식을 섞지 않았는가?
- [ ] Escrow 실패 응답에서 `REC`가 제외되는가?
- [ ] ERD에 없는 필드를 임의로 추가하지 않았는가?
- [ ] 입력 검증 실패와 비즈니스 실패 상태 코드가 구분되는가?
- [ ] 에러 응답에 내부 정보나 입력값이 노출되지 않는가?
- [ ] 타인의 민감한 리소스가 404로 처리되는가?
- [ ] N+1과 불필요한 EAGER 조회가 없는가?
- [ ] 바닐라 JPA로 표현할 수 없는 조회를 문자열 SQL이 아니라 QueryDSL로 작성했는가?
- [ ] QueryDSL 조회가 필요한 컬럼만 DTO로 가져오고 페이지 조건을 적용하는가?
- [ ] PostgreSQL 기반 통합 테스트가 필요한 변경인가?
- [ ] 스키마 변경이 새 Flyway migration으로 작성되었는가?
- [ ] 조회 요청을 어느 인스턴스가 받아도 동작하는 무상태 구현인가?
- [ ] 조회 API는 Writer와 Reader 양쪽에서 실행 가능한가?
- [ ] 쓰기 API와 상태 변경 작업은 Writer에서만 실행되는가?
- [ ] Reader DB 계정에 실제 read-only 권한이 적용되는가?
- [ ] Writer 전환 중 두 Writer가 생기지 않도록 fencing이 적용되는가?
- [ ] 재시도·중복 요청·동시 실행에도 상태가 중복 변경되지 않는가?
- [ ] 스케줄러 또는 consumer가 이중 실행되어도 안전한가?
- [ ] 롤링 배포 중 구버전과 신버전이 공존할 수 있는 변경인가?
- [ ] secret 또는 개인정보가 코드·설정·로그에 포함되지 않았는가?
- [ ] 정상·실패·권한 테스트가 작성되었는가?
- [ ] SonarQube에서 이번 변경으로 새로 생긴 이슈와 보안 핫스팟을 확인했는가?
- [ ] 남겨 둔 이슈에 대해 남기는 이유가 설명되어 있는가?

## 22. 완료 정의(Definition of Done)

백엔드 API 한 건은 다음을 모두 만족해야 완료로 봅니다.

1. 사용자 플로우와 API 책임이 연결되어 있습니다.
2. 요청·응답 필드가 ERD와 합의된 외부 명세에 근거합니다.
3. 목 Swagger가 최신 계약을 보여줍니다.
4. 실제 Spring API가 목 계약과 일치합니다.
5. 권한, 상태 전이, 동시성 및 멱등성 규칙이 Service에 구현되어 있습니다.
6. 조회는 두 인스턴스에서 가능하고 쓰기는 현재 Writer 하나에서만 가능하도록 라우팅·애플리케이션 guard·DB 권한이 적용되어 있습니다.
7. 필요 시 Flyway migration과 ERD가 함께 갱신되었습니다.
8. 단위·통합·Controller 테스트가 변경 위험에 맞게 작성되었습니다.
9. `gradlew build`가 통과합니다.
10. SonarQube 분석 결과를 확인했습니다. Quality Gate가 통과하고, 이번 변경으로 새로 생긴 이슈와 보안 핫스팟이 정리되어 있습니다.
11. API·DB·보안·이중화 영향이 PR 설명에 기록되어 있습니다.
