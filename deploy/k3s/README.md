# K3s 배포

`service`와 `escrow`를 각각 2개의 Pod로 실행하고, 두 서버의 DB를 CloudNativePG 클러스터로 분리합니다. 각 DB 앞에는 Pgpool-II를 두어 쓰기는 Primary로, 조회는 Standby로 나눕니다.

```text
service Pod ×2 ── service-db-pool (Pgpool ×2) ──┬─ 쓰기 → service-db-rw ─ Primary
                                                └─ 조회 → service-db-ro ─ Standby ×2

escrow Pod ×2 ─── escrow-db-pool (Pgpool ×2) ───┬─ 쓰기 → escrow-db-rw ── Primary
                                                └─ 조회 → escrow-db-ro ── Standby ×2
```

| 디렉터리 | 내용 |
|---|---|
| `platform/` | CloudNativePG 오퍼레이터 (K3s HelmChart) |
| `database/` | `service-db`, `escrow-db` Cluster와 Pgpool Deployment |
| `pgpool/` | Pgpool-II 4.6.7 이미지 |
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

1. `service`, `escrow`, `pgpool` Docker 이미지 빌드
2. 이미지를 K3s가 시작할 때 가져갈 tar 파일로 저장
3. privileged Linux 컨테이너에서 단일 노드 K3s 실행
4. CloudNativePG 오퍼레이터 설치
5. `service-db`, `escrow-db`(각 Primary 1 + Standby 2)와 Pgpool 배포
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
- 레지스트리에 올라간 `komit-service`, `komit-escrow`, `komit-pgpool` 이미지
- 노드 3대 이상 (Primary와 Standby가 서로 다른 노드에 있어야 노드 장애를 견딥니다)

이미지가 private이면 해당 네임스페이스에 image pull secret을 만들고 Deployment의 `imagePullSecrets`에 연결해야 합니다.

## 배포

다음 환경변수를 배포 서버나 CI의 보호된 변수로 주입합니다. 비밀번호를 저장소 파일에 기록하지 마세요.

```bash
export SERVICE_IMAGE=docker.io/<account>/komit-service:<tag>
export ESCROW_IMAGE=docker.io/<account>/komit-escrow:<tag>
export PGPOOL_IMAGE=docker.io/<account>/komit-pgpool:<tag>

export SERVICE_DB_PASSWORD=<password>
export SERVICE_DB_MONITOR_PASSWORD=<password>
export ESCROW_DB_PASSWORD=<password>
export ESCROW_DB_MONITOR_PASSWORD=<password>

chmod +x deploy/k3s/deploy.sh
./deploy/k3s/deploy.sh
```

스크립트는 DB 계정 Secret을 만들고, 배포할 이미지 태그를 `.generated/` 아래 Kustomize overlay에 고정해 적용합니다. 각 단계의 준비 완료를 기다린 뒤 다음 단계로 넘어갑니다.

| Secret | 사용처 |
|---|---|
| `service-db-app`, `escrow-db-app` | DB 소유자 계정. CloudNativePG initdb, Pgpool, Spring이 함께 읽습니다. |
| `service-db-pgpool-monitor`, `escrow-db-pgpool-monitor` | `pg_monitor` 권한만 가진 Pgpool 복제 지연 검사 계정 |

## 확인

```bash
kubectl -n komit get cluster
kubectl -n komit get pods -o wide -L role

kubectl -n komit port-forward service/service 8080:8080
curl http://localhost:8080/actuator/health
```

Pgpool이 보는 노드 상태와 노드별 조회 수는 `SHOW POOL_NODES`로 확인합니다.

```bash
kubectl -n komit exec service-db-1 -c postgres -- \
  sh -c "PGPASSWORD=<password> psql -h service-db-pool -U service -d service -c 'show pool_nodes'"
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
- **장애 전환:** CloudNativePG가 Primary 장애를 감지해 Standby를 승격하고, `-rw` Service를 새 Primary로 옮깁니다. Pgpool의 failover와 health check는 꺼 두어 판단 주체를 하나로 유지합니다.
- **동기 복제:** `synchronous_commit = remote_apply`와 `ANY 2` 동기 복제로, Standby가 반영까지 마친 뒤 커밋을 확정합니다. Pgpool이 조회를 Standby로 보내도 커밋된 쓰기를 읽습니다. `dataDurability: preferred`라서 Standby가 모두 내려가도 Primary 쓰기는 계속됩니다.
- **읽기/쓰기 분리:** Pgpool이 SQL을 해석해 조회만 Standby로 보냅니다. `SELECT ... FOR UPDATE`, volatile 함수(`nextval`, `pg_advisory_lock` 등)가 들어간 조회, 시스템 카탈로그 조회, 쓰기 이후의 같은 트랜잭션 조회는 Primary로 갑니다.
- **복제 지연 차단:** 지연이 1초를 넘은 Standby에는 조회를 보내지 않습니다.
- **빠른 전환:** Pgpool이 Primary 연결을 캐시로 유지하므로 `smartShutdownTimeout`을 15초로 줄였습니다. 기본값 180초에서는 Primary Pod 종료 시 전환이 약 3분 걸렸고, 15초로 줄인 뒤 약 20초로 줄었습니다.

### 알려진 제약
- **첫 쓰기 전 조회:** 쓰기 트랜잭션 안에서도 첫 쓰기 전의 SELECT는 Standby로 갈 수 있습니다. 동기 복제로 커밋된 데이터는 보이지만, 조회 후 갱신하는 로직은 `@Version`이나 조건부 UPDATE로 동시성을 보호해야 합니다.
- **Standby 전체 장애:** Pgpool은 세션을 열 때 두 백엔드에 모두 연결합니다. 그래서 Standby가 모두 내려가 `-ro` Service에 대상이 없으면 새 DB 연결이 실패합니다. Standby를 2대 이상 유지하는 이유입니다.
- **Pgpool 이미지:** 공식 `pgpool/pgpool` 이미지는 4.4.3(2023)에서 갱신이 멈춰 CVE-2025-46801 영향 버전입니다. Alpine 패키지로 4.6.7을 고정해 직접 빌드하므로, 보안 수정이 나오면 `pgpool/Dockerfile`의 버전을 올려야 합니다.
