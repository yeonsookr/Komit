# K3s 배포

`service`와 `escrow`를 각각 2개의 Pod로 실행하고, 두 서버의 DB를 CloudNativePG 클러스터로 분리합니다. Spring이 트랜잭션 성격에 따라 쓰기는 Primary로, `readOnly` 조회는 Standby로 보냅니다.

```text
service Pod ×2 ─┬─ 일반 트랜잭션   → service-db-rw ─ Primary
                └─ readOnly 트랜잭션 → service-db-ro ─ Standby ×2

escrow Pod ×2 ──┬─ 일반 트랜잭션   → escrow-db-rw ── Primary
                └─ readOnly 트랜잭션 → escrow-db-ro ── Standby ×2
```

| 디렉터리 | 내용 |
|---|---|
| `platform/` | CloudNativePG 오퍼레이터 (K3s HelmChart) |
| `database/` | `service-db`, `escrow-db` Cluster |
| `base/` | `service`, `escrow` 애플리케이션 |
| `local/` | 로컬 실습용 Secret, NodePort, 이미지 태그 |

CloudNativePG Cluster는 오퍼레이터의 CRD가 설치된 뒤에만 만들 수 있습니다. 그래서 오퍼레이터 → DB → 애플리케이션 순서로 나눠 적용합니다.

## Windows에서 한 번에 실행

Docker Desktop의 Linux 컨테이너 모드에서 저장소 루트 기준으로 실행합니다. CloudNativePG 차트와 PostgreSQL 이미지를 인터넷에서 받습니다.

```powershell
docker compose -f .\deploy\k3s\docker-compose.yml up -d
docker compose -f .\deploy\k3s\docker-compose.yml logs -f deployer
```

Compose가 다음 작업을 순서대로 수행합니다.

1. `service`, `escrow` Docker 이미지 빌드
2. 이미지를 K3s가 시작할 때 가져갈 tar 파일로 저장
3. privileged Linux 컨테이너에서 단일 노드 K3s 실행
4. CloudNativePG 오퍼레이터 설치
5. `service-db`, `escrow-db`(각 Primary 1 + Standby 2) 배포
6. `service`, `escrow`를 각각 2개 Pod로 배포

상태와 접속을 확인합니다.

```powershell
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit get cluster
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit get pods -o wide -L role
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8081/actuator/health
```

중지와 재시작 시 데이터는 유지됩니다.

```powershell
docker compose -f .\deploy\k3s\docker-compose.yml down
docker compose -f .\deploy\k3s\docker-compose.yml up -d
```

클러스터와 로컬 DB 데이터까지 모두 지우려는 경우에만 `down -v`를 사용합니다.

```powershell
docker compose -f .\deploy\k3s\docker-compose.yml down -v
```

K3s는 tar 이미지를 시작할 때만 가져옵니다. 이미지를 다시 빌드했다면 `down` 후 `up -d`로 K3s를 재시작합니다.

## 준비물

- 동작 중인 K3s 클러스터와 해당 클러스터를 가리키는 `kubectl`
- 레지스트리에 올라간 `komit-service`, `komit-escrow` 이미지
- 노드 3대 이상 (Primary와 Standby가 서로 다른 노드에 있어야 노드 장애를 견딥니다)

이미지가 private이면 해당 네임스페이스에 image pull secret을 만들고 Deployment의 `imagePullSecrets`에 연결해야 합니다.

## 배포

다음 환경변수를 배포 서버나 CI의 보호된 변수로 주입합니다. 비밀번호를 저장소 파일에 기록하지 마세요.

```bash
export SERVICE_DB_PASSWORD=<password>
export ESCROW_DB_PASSWORD=<password>

# 이번에 바뀐 모듈만 넘깁니다. 첫 배포에는 둘 다 필요합니다.
export SERVICE_IMAGE=docker.io/<account>/komit-service:<commit-sha>
export ESCROW_IMAGE=docker.io/<account>/komit-escrow:<commit-sha>

chmod +x deploy/k3s/deploy.sh
./deploy/k3s/deploy.sh
```

| 환경변수 | 필수 | 설명 |
|---|---|---|
| `SERVICE_DB_PASSWORD`, `ESCROW_DB_PASSWORD` | 예 | DB 소유자 계정 비밀번호. `service-db-app`, `escrow-db-app` Secret이 되어 CloudNativePG initdb와 Spring이 함께 읽습니다. |
| `SERVICE_IMAGE`, `ESCROW_IMAGE` | 아니요 | 비어 있으면 클러스터에서 돌고 있는 이미지를 유지합니다. 바뀌지 않은 모듈은 재시작하지 않습니다. |

스크립트는 배포할 이미지 태그를 임시 디렉터리의 Kustomize overlay에 고정해 적용하고, 각 단계의 준비 완료를 기다린 뒤 다음 단계로 넘어갑니다. DB 주소는 클러스터 내부 DNS 이름으로 고정되어 있어 주입하지 않습니다.

## 확인

```bash
kubectl -n komit get cluster
kubectl -n komit get pods -o wide -L role

kubectl -n komit port-forward service/service 8080:8080
curl http://localhost:8080/actuator/health
```

## 스케일 조정

기본 replica는 2개이며, HPA가 평균 CPU 사용률 70%를 기준으로 최대 5개까지 자동 확장합니다. K3s는 Metrics Server를 기본 배포하지만 설치할 때 비활성화했다면 HPA 사용 전에 다시 활성화해야 합니다.

Standby 수는 Cluster의 `instances`로 조정합니다. 동기 복제 대상 수(`synchronous.number`)도 함께 맞춥니다.

## 구성 의도

### 애플리케이션
- `Deployment`: Pod 2개 유지, 무중단 롤링 업데이트
- `Service`: 준비 상태를 통과한 Pod에만 내부 트래픽 분산
- `startup/readiness/liveness probe`: Spring Boot Actuator로 기동·트래픽 수신·장애 상태 구분
- `topologySpreadConstraints`: 여러 Worker에 가능한 한 균등 배치
- `HorizontalPodAutoscaler`: CPU 부하에 따라 2~5개 Pod로 자동 확장
- `PodDisruptionBudget`: 노드 정비 중 두 replica가 동시에 사라지는 것을 방지
- 컨테이너 보안 설정: non-root 실행, 권한 상승 차단, 읽기 전용 루트 파일시스템

### DB
- **서버별 DB 분리:** `service`와 `escrow`는 서로 다른 CloudNativePG Cluster를 씁니다. 한쪽 DB 장애나 부하가 다른 쪽에 번지지 않습니다.
- **장애 전환:** CloudNativePG가 Primary 장애를 감지해 Standby를 승격하고, `-rw` Service를 새 Primary로 옮깁니다.
- **동기 복제:** `synchronous_commit = remote_apply`와 `ANY 2` 동기 복제로, Standby가 반영까지 마친 뒤 커밋을 확정합니다. `readOnly` 조회가 Standby에서 읽어도 커밋된 쓰기를 봅니다. `dataDurability: preferred`라서 Standby가 모두 내려가도 Primary 쓰기는 계속됩니다.
- **빠른 전환:** 앱 커넥션 풀이 Primary 연결을 유지하므로 `smartShutdownTimeout`을 15초로 줄였습니다. 기본값 180초에서는 Primary Pod 종료 시 전환이 약 3분 걸렸습니다.

### 읽기/쓰기 분리
각 서버의 `config/DataSourceRoutingConfig`가 쓰기 풀(`DB_URL`)과 읽기 풀(`DB_READ_URL`)을 만들고, `LazyConnectionDataSourceProxy`로 트랜잭션마다 풀을 고릅니다.

| 호출 | 접속 DB |
|---|---|
| `@Transactional(readOnly = true)` | 읽기 풀 → `-ro` (Standby) |
| `@Transactional`, 트랜잭션 없는 쿼리 | 쓰기 풀 → `-rw` (Primary) |
| Flyway 마이그레이션, Hibernate 스키마 검증 | 쓰기 풀 → `-rw` (Primary) |

- `DB_READ_URL`이 없으면 쓰기 주소를 그대로 씁니다. `local` 프로필과 CI 테스트는 DB 하나로 동작합니다.
- 조회 메서드에 `readOnly = true`를 붙여야 Standby로 갑니다. 쓰기 직후 최신값이 반드시 필요한 조회는 `readOnly`를 빼서 Primary에서 읽습니다.

### 알려진 제약
- **Standby 전체 장애:** Standby가 모두 내려가 `-ro` Service에 대상이 없으면 `readOnly` 트랜잭션이 실패합니다. 쓰기 트랜잭션은 계속 동작합니다. Standby를 2대 이상 유지하는 이유입니다.
- **Flyway 실행 위치:** Flyway는 앱 Pod가 시작할 때마다 실행됩니다. PostgreSQL advisory lock으로 동시 실행은 막히지만, 백엔드 컨벤션 6.7(마이그레이션은 배포 파이프라인의 한 단계에서 실행)과는 아직 다릅니다.
