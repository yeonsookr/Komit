# Escrow 고가용성 배포 기준

이 문서는 `docs/BACKEND_CONVENTIONS.md`의 Single Writer 원칙을 Escrow 운영 환경에 적용하는 기준이다. 현재 `deploy/k3s/base`는 Escrow Pod 두 개에 모두 `APP_INSTANCE_ROLE=WRITER`를 넣고 하나의 Kubernetes Service로 전달하므로 이 목표 구조와 다르다. 인프라 작업에서는 아래 조건을 함께 반영해야 한다.

## 1. 목표 구조

Escrow 애플리케이션은 같은 artifact를 사용하되 런타임 역할을 분리한다.

| 구성 | 수량 | 처리 범위 |
| --- | ---: | --- |
| Escrow Writer | 정확히 1개 | 조회, 상태 변경, 스케줄러, 금융망 호출 |
| Escrow Reader | 1개 이상 | 부작용 없는 조회만 처리 |
| PostgreSQL Primary | 1개 | 모든 쓰기 트랜잭션 |
| PostgreSQL Standby | 1개 이상 | 일반 조회와 동기 복제 |

Service 서버가 상태를 바꾸는 Escrow API를 호출할 때는 Escrow write Service를 사용한다. 일반 조회는 read Service를 사용할 수 있다. 쓰기 직후 확인, 멱등 결과 확인, `UNKNOWN` 복구 및 대사처럼 최신 상태가 필요한 조회는 Writer와 Primary DB를 사용한다.

## 2. Kubernetes 리소스 분리

Escrow Deployment를 `escrow-writer`와 `escrow-reader`로 분리하고 역할을 각 Deployment에 직접 주입한다.

```text
escrow-writer: replicas=1, APP_INSTANCE_ROLE=WRITER
escrow-reader: replicas>=1, APP_INSTANCE_ROLE=READER
```

공통 ConfigMap에는 `APP_INSTANCE_ROLE`을 두지 않는다. Pod에는 공통 이름 라벨과 함께 `app.kubernetes.io/role=writer|reader`를 붙인다.

Kubernetes Service도 다음처럼 나눈다.

| Service | selector | 용도 |
| --- | --- | --- |
| `escrow-write` | `name=escrow`, `role=writer` | 생성·변경·금융 처리와 일관성 필수 조회 |
| `escrow-read` | `name=escrow` | 일반 조회를 Writer와 Reader에 분산 |

Ingress 또는 상위 프록시는 HTTP method만으로 판단하지 않고 Mock API 명세에 정의된 실제 동작을 기준으로 두 Service에 경로를 연결한다. 조회 성격의 POST도 read route로 명시하고, 상태를 바꾸는 모든 요청은 write route로 보낸다.

## 3. 확장과 배포 전략

Writer에는 HPA를 적용하지 않는다. Reader만 HPA 대상으로 삼는다. Writer Deployment의 롤링 업데이트에서 두 Writer가 동시에 생성되지 않도록 `maxSurge: 0`을 사용하거나 `Recreate` 전략을 사용한다. 무중단 Writer 교체가 필요하면 배포 전에 외부 lease와 fencing을 먼저 구현한다.

Writer와 Reader는 서로 다른 노드에 배치되도록 topology spread 또는 Pod anti-affinity를 설정한다. 단일 노드 K3s는 Pod 재시작에는 대응할 수 있지만 노드 장애에는 대응하지 못하므로 운영 이중화 환경은 둘 이상의 worker node를 가져야 한다.

## 4. 애플리케이션과 DB 권한 방어

Reader로 쓰기 요청이 전달되지 않는 것이 첫 번째 방어다. 애플리케이션과 DB 권한에서도 다시 차단한다.

- Reader에서는 쓰기 Service, 상태 변경 스케줄러, consumer, 금융망 호출을 기동하거나 실행하지 않는다.
- Reader DB 계정에는 `INSERT`, `UPDATE`, `DELETE`, DDL 권한을 부여하지 않는다.
- Writer의 변경 트랜잭션은 PostgreSQL Primary의 읽기·쓰기 계정을 사용한다.
- 일반 `readOnly` 트랜잭션은 Standby의 읽기 전용 계정을 사용한다.
- 스키마 변경은 배포당 한 번 실행하는 migration Job 또는 동등한 단일 실행 절차로 수행하며 Reader에서는 실행하지 않는다.
- read readiness는 Writer와 Reader가 제공하고 write readiness는 현재 Writer만 통과한다.

`@Transactional(readOnly = true)`는 DB 권한을 대신하지 않는다. Reader가 쓰기를 시도하면 요청을 성공으로 숨기지 않고 실패 처리하고 운영 경보를 남긴다.

## 5. PostgreSQL 이중화

CloudNativePG의 `-rw` Service는 Primary에, `-ro` Service는 Standby에 연결한다. Escrow 자금 기록은 장애 전환 때 유실되면 금융망 실행 여부와 원장이 어긋날 수 있으므로 최소 한 Standby의 동기 반영을 커밋 조건으로 둔다.

권장 운영 기준은 다음과 같다.

```text
instances: 3
synchronous replication: ANY 1
dataDurability: required
synchronous_commit: remote_apply
```

동기 Standby를 확보하지 못한 동안 쓰기가 멈출 수 있다. Escrow에서는 쓰기 가용성보다 승인된 금융 기록의 보존을 우선한다. 일반 조회는 남아 있는 Reader와 Standby에서 계속 제공할 수 있다.

## 6. Writer 장애 전환과 fencing

Writer 장애 시 새 Writer가 금융 업무를 시작하기 전에 기존 Writer의 쓰기 가능성을 먼저 제거한다.

1. 기존 Writer를 write Service 대상에서 제거한다.
2. 기존 Writer Pod를 종료하고 DB 쓰기 권한 또는 lease를 회수한다.
3. 기존 Writer가 더 이상 Primary DB와 금융망을 호출할 수 없음을 확인한다.
4. 새 Writer 하나에 역할, DB 권한 및 write readiness를 부여한다.
5. 새 Writer를 write Service에 등록한다.
6. `DISPATCHING`과 `UNKNOWN` 금융 시도를 조회하고 대사한 뒤 업무를 재개한다.

환경 변수 변경만으로 fencing을 완료한 것으로 보지 않는다. 네트워크가 단절됐던 이전 Writer가 다시 살아나도 DB 쓰기와 금융망 호출을 수행할 수 없도록 DB 권한, 외부 lease 또는 같은 수준의 차단 수단이 필요하다.

## 7. 금융 요청 복구 조건

Writer 교체 전후의 같은 명령은 동일한 `idempotencyKey`와 요청 지문을 유지한다. 금융망 호출 시도는 다음 순서로 실행한다.

```text
REQUESTED
  → 원자적 갱신으로 실행권 획득
DISPATCHING(dispatched_by, dispatched_at)
  → SUCCEEDED | FAILED | UNKNOWN
```

`DISPATCHING` 상태에서 Writer가 중단되면 요청이 외부 금융망에 도달했을 가능성이 있으므로 새 Writer가 같은 송금을 즉시 다시 보내지 않는다. 해당 시도를 `UNKNOWN`으로 관리하고 기존 요청 식별자와 금융 거래 내역으로 결과를 확인한다. 자세한 상태와 금융 API 매핑은 `FINANCE_MAPPING.md`를 따른다.

## 8. 배포 완료 조건

- 동시에 write readiness를 통과하는 Escrow Pod가 정확히 하나다.
- Reader DB 계정으로 DML과 DDL이 실패한다.
- write Service가 Reader Pod를 endpoint로 포함하지 않는다.
- Writer 교체 중 같은 멱등 키의 상태 변경과 금융 요청이 한 번만 반영된다.
- Writer가 `DISPATCHING` 직후 종료된 경우 새 Writer가 송금을 재실행하지 않고 `UNKNOWN` 복구 절차로 진입한다.
- PostgreSQL Primary 장애 전환 뒤 커밋 완료된 금융 시도와 원장 기록이 보존된다.
- 로그에서 `instanceId`, 애플리케이션 버전, 역할 및 요청 `traceId`를 확인할 수 있다.
