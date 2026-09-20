# Escrow 고가용성 배포 기준

이 문서는 Escrow의 합의된 고가용성 구조를 정의한다. 애플리케이션 서버는 Active-Active로 운영하고, 데이터베이스는 Primary-Standby로 운영한다. 모든 애플리케이션 Pod가 API를 처리할 수 있지만, 같은 금융 업무의 외부 호출 실행권은 DB의 원자적 상태 전이로 정확히 한 Pod만 획득해야 한다.

## 1. 목표 구조

| 구성 | 수량 | 처리 범위 |
| --- | ---: | --- |
| Escrow 애플리케이션 | 2개 이상 | 모든 Pod가 조회·상태 변경 요청을 처리하는 Active-Active |
| PostgreSQL Primary | 1개 | 모든 쓰기 트랜잭션과 최신 상태가 필요한 조회 |
| PostgreSQL Standby | 1개 이상 | 동기 복제, 일반 읽기, Primary 장애 시 승격 |

```text
Escrow Pod A ─┐
              ├─ PostgreSQL Primary에서 거래별 실행권 경쟁
Escrow Pod B ─┘
                         │
                 실행권을 얻은 한 Pod
                         │
                    SSAFY 금융망
```

애플리케이션 Pod 전체를 Writer와 Reader로 나누지 않는다. `APP_INSTANCE_ROLE=WRITER`는 모든 Active Pod에 적용한다. 여기서 Writer는 Pod가 단독 Active라는 뜻이 아니라, DB 실행권을 획득하면 쓰기와 금융 호출을 수행할 수 있는 Active 인스턴스라는 뜻이다.

## 2. Kubernetes 배포

기존 리소스 이름과 외부 경로를 유지한다.

- `deployment/escrow`는 `replicas >= 2`로 운영한다.
- `service/escrow`는 모든 Ready Escrow Pod에 요청을 분산한다.
- 운영 NodePort와 nginx는 기존 `service/escrow`를 계속 사용한다.
- RollingUpdate 중 구버전과 신버전이 공존할 수 있으므로 DB 스키마는 expand/contract 방식으로 변경한다.
- `maxSurge`로 일시적으로 Pod가 늘어나도 거래별 실행권 불변식이 유지되어야 한다.
- HPA를 사용하더라도 Pod 수를 정확성 조건으로 사용하지 않는다.

Pod는 무상태여야 하며 `POD_NAME` 같은 생명주기 고유값을 `instanceId`로 기록한다. 로컬 JVM lock, `synchronized`, Pod 수 또는 로드밸런서의 우연한 분산 결과에 금융 정확성을 의존하지 않는다.

## 3. 금융 호출 단일 실행

모든 Pod가 요청을 받을 수 있지만 동일 금융 시도의 실행자는 하나여야 한다.

```text
REQUESTED
  → 조건부 UPDATE 성공한 한 Pod가 실행권 획득
DISPATCHING(dispatched_by, dispatched_at)
  → SUCCEEDED | FAILED | UNKNOWN
```

- 외부 호출 전에 금융 시도와 `request_reference`를 DB에 저장한다.
- `REQUESTED → DISPATCHING` 조건부 갱신이 1행 성공한 Pod만 금융망을 호출한다.
- `dispatched_by`에는 Pod 이름처럼 실행 생명주기 동안 고유한 값을 저장한다.
- 같은 거래·단계에는 미확정 시도를 하나만 허용하는 Unique 제약을 둔다.
- 상태 변경과 멱등 결과는 하나의 DB 트랜잭션으로 확정한다.
- 같은 `idempotencyKey`와 요청 지문은 어느 Pod가 받아도 동일한 결과를 반환한다.
- 외부 호출을 DB 트랜잭션 안에서 오래 유지하지 않는다.

## 4. 응답 유실과 Pod 장애 복구

`DISPATCHING` 상태에서 Pod가 종료되면 요청이 금융망에 도달했을 가능성이 있다. 다른 Pod는 같은 송금을 즉시 다시 보내지 않는다.

1. 기존 시도를 `UNKNOWN` 복구 대상으로 식별한다.
2. 원래 `request_reference`, 거래 요약, 금액, 계좌와 시간 범위로 금융망 거래내역을 조회한다.
3. 유일하게 일치하는 거래가 확인되면 원장과 시도를 성공으로 복구한다.
4. 확인할 수 없으면 `UNKNOWN`을 유지하고 운영 확인 대상으로 남긴다.
5. 조회 시도와 판단 근거를 감사 사건으로 추가 기록한다.

스케줄러는 모든 Pod에서 기동될 수 있지만, 처리 대상별 DB claim 또는 분산 lock을 획득한 Pod만 상태를 변경한다. 대사처럼 전체 범위에서 한 번만 실행해야 하는 작업은 PostgreSQL advisory lock 등 DB 기반 분산 lock을 사용한다.

## 5. PostgreSQL Primary-Standby

CloudNativePG의 `-rw` Service는 현재 Primary로, `-ro` Service는 Standby로 연결한다.

- 변경 트랜잭션은 반드시 `-rw`를 사용한다.
- 멱등 결과 확인, 금융 복구, 대사와 쓰기 직후 조회는 Primary를 사용한다.
- 복제 지연을 허용할 수 있는 일반 조회만 `-ro`를 사용할 수 있다.
- 최소 한 Standby의 동기 반영을 커밋 조건으로 둔다.
- Primary 장애 시 CloudNativePG가 Standby를 승격하고 `-rw` Service를 새 Primary로 전환한다.
- 애플리케이션 Pod는 DB 역할 전환 후 연결을 회복하고 미확정 금융 시도를 복구한다.

권장 운영 기준은 다음과 같다.

```text
instances: 3
synchronous replication: ANY 1 이상
synchronous_commit: remote_apply
```

가용성과 내구성 설정이 실제 배포 manifest와 다르면 배포 전에 차이를 명시한다. 커밋 완료된 금융 시도와 원장 기록 보존을 쓰기 가용성보다 우선한다.

## 6. 데이터베이스 권한과 migration

- Primary 쓰기 계정과 Standby 읽기 계정을 분리한다.
- 읽기 계정에는 `INSERT`, `UPDATE`, `DELETE`, DDL 권한을 부여하지 않는다.
- `@Transactional(readOnly = true)`만으로 권한 경계를 대신하지 않는다.
- Flyway는 배포당 한 번 실행하는 migration Job 또는 동등한 단일 실행 절차로 수행한다.
- 일반 애플리케이션 Pod의 동시 기동 횟수에 migration 실행 횟수가 좌우되지 않게 한다.

## 7. 실제 금융 환경 검증

승인된 API 키와 테스트 계좌에서만 실제 호출을 수행한다. 테스트는 호출 전후 잔액과 금융 거래번호를 기록하고 마지막에 대사한다.

- 회원·계좌 발급과 데모 입금
- 1원 인증 요청과 확인
- 가상계좌 생성, 예치, 보관계좌 정산
- 지급, 수수료 이체, 환불
- 응답 유실 또는 Pod 종료 후 거래내역 기반 복구
- 원장 합계와 실제 보관계좌 잔액 대사

실제 자금 이동 테스트는 승인된 소액과 테스트 계좌로 제한한다. 테스트 실패를 이유로 같은 송금을 새 식별자로 즉시 재전송하지 않는다.

### 2026-09-20 검증 기록

- 두 실행 주체가 같은 금융 복구 건을 동시에 claim하면 DB 제약과 원자적 INSERT에 의해 하나만 성공하는 통합 테스트를 통과했다.
- `DISPATCHING` 상태에서 응답을 기록하지 못한 시도를 시간 초과 후 `UNKNOWN`으로 전환하고, 송금을 재실행하지 않은 채 결과 조회 실행권만 하나의 Pod가 획득하는 통합 테스트를 통과했다.
- 승인된 SSAFY 테스트 계좌에서 에스크로 계좌 → 수수료 계좌 1원 이체와 반대 방향의 원복 이체를 실제 호출했다. 거래내역으로 중단 이체를 먼저 대조했으며, 왕복 후 두 계좌 잔액이 시작값과 같음을 확인했다.
- 전체 Gradle 테스트, 실행 JAR, Docker 이미지, 운영 Kustomize 렌더링과 `deploy.sh` 셸 문법 검사를 통과했다.
- 실제 K3s의 다중 Pod 트래픽 분산과 PostgreSQL Primary 장애 승격 시험은 배포 환경에서 별도로 수행해야 한다. 애플리케이션 코드 검증만으로 이 항목을 통과한 것으로 간주하지 않는다.

## 8. 배포 완료 조건

- Escrow Pod 두 개 이상이 동시에 Ready이고 어느 Pod로도 API 요청을 처리할 수 있다.
- 같은 멱등키를 여러 Pod에 동시에 보내도 업무 상태와 금융 호출이 한 번만 반영된다.
- RollingUpdate로 Pod가 일시적으로 늘어나도 동일 금융 시도의 `DISPATCHING` claim은 하나만 성공한다.
- `DISPATCHING` 직후 Pod가 종료되면 다른 Pod가 송금을 재실행하지 않고 `UNKNOWN` 복구 절차로 진입한다.
- 상태 변경 스케줄러와 대사가 중복 실행되어도 DB claim 또는 분산 lock으로 한 번만 반영된다.
- PostgreSQL Primary 장애 전환 뒤 커밋 완료된 금융 시도와 원장 기록이 보존된다.
- 일반 조회의 Standby 복제 지연이 금융 판단이나 쓰기 직후 결과를 되돌리지 않는다.
- 로그에서 `instanceId`, 애플리케이션 버전, 요청 `traceId`, 금융 시도의 `dispatched_by`를 확인할 수 있다.
