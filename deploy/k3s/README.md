# K3s 배포

`service`, `escrow`, `fe`를 각각 2개의 Pod로 실행하고, 두 서버의 DB를 CloudNativePG 클러스터로 분리합니다. `fe`(Next.js 서버)는 클러스터 안에서 Service 이름으로 스프링을 호출합니다. Spring이 트랜잭션 성격에 따라 쓰기는 Primary로, `readOnly` 조회는 Standby로 보냅니다. `service`는 refresh token 저장과 캐시에 Redis를 쓰고, Redis는 Sentinel이 감시하는 Primary 1 + Replica 2로 둡니다.

```text
service Pod ×2 ─┬─ 일반 트랜잭션   → service-db-rw ─ Primary
                ├─ readOnly 트랜잭션 → service-db-ro ─ Standby ×2
                └─ Redis ─ service-redis-sentinel:26379 ─(현재 Primary 주소)→ service-redis Primary
                                                                              └─ Replica ×2

escrow Pod ×2 ──┬─ 일반 트랜잭션   → escrow-db-rw ── Primary
                └─ readOnly 트랜잭션 → escrow-db-ro ── Standby ×2
```

| 디렉터리 | 내용 |
|---|---|
| `platform/` | CloudNativePG 오퍼레이터, redis-operator (K3s HelmChart) |
| `database/` | `service-db`, `escrow-db` Cluster, `service-redis` RedisReplication + RedisSentinel |
| `base/` | `service`, `escrow`, `fe` 애플리케이션 |
| `prod/` | 운영 overlay. `service`(30080), `escrow`(30081), 실제 BE용 `fe`(30082), mock용 `fe-test`(30083)를 nginx용 NodePort로 엽니다 |
| `local/` | 로컬 실습용 Secret, NodePort, 이미지 태그와 mock |

CloudNativePG Cluster와 Redis 리소스는 각 오퍼레이터의 CRD가 설치된 뒤에만 만들 수 있습니다. 그래서 오퍼레이터 → DB·Redis → 애플리케이션 순서로 나눠 적용합니다.

### Redis 구성

- Redis 3노드(`service-redis-0~2`)와 Sentinel 3개(`service-redis-sentinel-0~2`)를 [OT-CONTAINER-KIT redis-operator](https://github.com/OT-CONTAINER-KIT/redis-operator)가 관리합니다. Primary가 죽으면 Sentinel 2개 이상(quorum)이 동의해 Replica 하나를 승격하고, 오퍼레이터가 Pod의 `redis-role` 라벨을 다시 맞춥니다.
- Spring은 `service-redis-sentinel:26379`에 현재 Primary 주소를 물어 붙습니다. 오퍼레이터가 만드는 `service-redis-master` Service는 확인용으로만 씁니다. Redis 복제는 비동기라 failover 순간의 마지막 쓰기는 유실될 수 있습니다.
- 스토리지(PVC 1Gi)를 지정해 오퍼레이터가 RDB + AOF 영속화를 켭니다. `database/service-redis.yaml`의 ConfigMap이 `maxmemory-policy noeviction`을 더해 refresh token이 메모리 부족으로 지워지지 않게 합니다. 캐시 키는 반드시 TTL을 달아야 합니다.
- Redis·Sentinel 비밀번호는 `service-redis-auth` Secret 하나를 함께 씁니다. 운영은 `deploy.sh`가 `SERVICE_REDIS_PASSWORD`로 만들고, 로컬은 `local/database/secrets.yaml`에 고정 값이 있습니다.
- 지금 K3s는 노드 한 대이므로 Sentinel은 Redis Pod 장애만 막고 노드 장애는 막지 못합니다. PostgreSQL 3인스턴스와 같은 한계입니다.

## Windows에서 한 번에 실행

Docker Desktop의 Linux 컨테이너 모드에서 저장소 루트 기준으로 실행합니다. CloudNativePG 차트와 PostgreSQL 이미지를 인터넷에서 받습니다.

```powershell
docker compose -f .\deploy\k3s\docker-compose.yml up -d
docker compose -f .\deploy\k3s\docker-compose.yml logs -f deployer
```

Compose가 다음 작업을 순서대로 수행합니다.

1. `service`, `escrow`, `mock`, `fe`, `fe-test` Docker 이미지 빌드
2. 이미지를 K3s가 시작할 때 가져갈 tar 파일로 저장
3. privileged Linux 컨테이너에서 단일 노드 K3s 실행
4. CloudNativePG 오퍼레이터, redis-operator 설치
5. `service-db`, `escrow-db`(각 Primary 1 + Standby 2), `service-redis`(Primary 1 + Replica 2, Sentinel 3) 배포
6. `service`, `escrow`, 기본 `fe`와 mock용 `fe-test`를 배포

상태와 접속을 확인합니다.

```powershell
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit get cluster
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit get redisreplication,redissentinel
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit get pods -o wide -L role,redis-role
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8081/actuator/health
curl.exe http://localhost:3000/
curl.exe http://localhost:3001/test
```

Redis failover를 눈으로 확인하려면 Primary Pod를 지우고 다른 Pod가 `redis-role=master`가 되는지 봅니다.

```powershell
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit delete pod -l redis-role=master,app=service-redis
docker exec komit-k3s-k3s-1 /bin/kubectl -n komit get pods -L redis-role -l app=service-redis -w
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

## 운영 서버 준비 (최초 1회)

목 서버가 도는 EC2에 K3s를 함께 설치합니다. Docker와 K3s는 컨테이너 런타임이 분리되어 있어 같이 설치해도 됩니다.

```text
인터넷 ─ 80/443 ─ nginx 컨테이너 ─┬─ /mock/    ─ mock 컨테이너
                                  ├─ /service/ ─ 172.17.0.1:30080 ─ K3s service Pod
                                  ├─ /escrow/  ─ 172.17.0.1:30081 ─ K3s escrow Pod
                                  ├─ /test     ─ 172.17.0.1:30083 ─ K3s fe-test Pod ─ mock 서버
                                  └─ /         ─ 172.17.0.1:30082 ─ K3s fe Pod ─ service Pod
```

기본 `fe`는 실제 BE를 보고 2벌로 뜹니다. `fe-test`는 `/test`에서 mock을 보는 1벌이며
HPA·PDB를 두지 않습니다. API 주소와 카카오 콜백은 CI/CD 변수부터 ConfigMap까지 서로
분리되어 한쪽 값을 바꿔도 다른 쪽이 바뀌지 않습니다.

### 1. K3s 설치

기존 nginx가 80/443을 쓰므로 traefik과 servicelb는 끄고, NodePort는 서버 내부 주소에만 엽니다.

```bash
ip -4 addr show docker0   # inet 172.17.0.1/16 → Docker 브리지 주소

curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="v1.35.2+k3s1" sh -s - server \
  --disable=traefik \
  --disable=servicelb \
  --secrets-encryption \
  --kube-proxy-arg=nodeport-addresses=127.0.0.1/32,172.17.0.1/32

sudo kubectl get nodes
```

- `--secrets-encryption`: DB 비밀번호 Secret을 암호화해 저장합니다.
- `nodeport-addresses`: `127.0.0.1`은 서버 자신, `172.17.0.1`은 nginx 컨테이너가 호스트로 들어오는 Docker 브리지 주소입니다. 이 두 주소에만 NodePort가 열려 인터넷에서 30080~30083으로 직접 들어올 수 없습니다. `docker0` 주소가 다르면 그 값으로 바꿉니다.

### 2. 배포 계정 권한

CI는 목 서버와 같은 SSH 계정으로 접속해 `sudo -n sh`로 `deploy.sh`를 실행합니다. 목 배포처럼 비밀번호 없는 sudo가 필요합니다.

### 3. Docker Hub 인증 (private 저장소일 때)

```bash
sudo tee /etc/rancher/k3s/registries.yaml >/dev/null <<'EOF'
configs:
  registry-1.docker.io:
    auth:
      username: <계정>
      password: <액세스 토큰>
EOF
sudo systemctl restart k3s
```

### 4. nginx 연결

nginx 컨테이너 안의 `127.0.0.1`은 호스트가 아니라 컨테이너 자신입니다. 그래서 호스트의 Docker 브리지 주소(`172.17.0.1`)로 접속합니다.

`conf.d/default.conf`에 추가합니다. `upstream`은 반드시 `server { }` 블록 **밖**에 둡니다. 기존의 `location = / { return 200 ... }`은 FE와 겹치므로 지웁니다.

```nginx
# WebSocket 업그레이드를 그대로 넘기기 위한 값입니다. conf.d 파일은 http 블록 안에
# 들어가므로 upstream 과 마찬가지로 server { } 밖에 둡니다.
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

# 질의 문자열을 뺀 접근 로그 형식입니다. WebSocket 주소에는 일회용 입장 티켓이 실려 오므로
# 그대로 남기면 로그를 읽을 수 있는 사람이 남의 연결을 열 수 있습니다.
log_format komit_ws '$remote_addr - $remote_user [$time_local] '
                    '"$request_method $uri $server_protocol" $status $body_bytes_sent '
                    '"$http_referer" "$http_user_agent"';

upstream komit_fe {
    server 172.17.0.1:30082;
    keepalive 16;
}

upstream komit_service {
    server 172.17.0.1:30080;
    keepalive 16;
}

upstream komit_escrow {
    server 172.17.0.1:30081;
    keepalive 16;
}

# mock을 보는 /test 전용 FE.
upstream komit_test {
    server 172.17.0.1:30083;
    keepalive 16;
}

server {
    # ...기존 설정 (/mock/, /grafana/ 등)...

    # 다른 location에 걸리지 않은 나머지 경로는 모두 FE로 보냅니다.
    location / {
        proxy_pass http://komit_fe;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 슬래시 없는 /escrow가 위의 FE location으로 빠지지 않도록 정규화합니다.
    location = /escrow {
        return 308 /escrow/;
    }

    location /escrow/ {
        proxy_pass http://komit_escrow/;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Prefix /escrow;
    }

    # WebSocket은 Upgrade 헤더가 그대로 가야 열립니다. 아래 `/service/`는 keepalive를 쓰려고
    # Connection을 비우는데, 그 설정이 WebSocket에 걸리면 업그레이드 신호가 지워져 핸드셰이크가
    # 평범한 HTTP 요청으로 끝납니다. nginx는 접두사가 더 긴 location을 먼저 고르므로 이 블록이
    # `/service/`보다 뒤에 있어도 됩니다.
    location /service/ws/ {
        proxy_pass http://komit_service/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Prefix /service;

        # 조용한 구간이 기본 60초를 넘으면 nginx가 먼저 끊습니다. 서버가 20초마다 확인하지만
        # 그보다 넉넉히 둬야 잠깐 밀렸다고 통화가 끊기지 않습니다.
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;

        # 모아서 보내면 통화를 여는 신호가 늦게 도착해 연결이 느려집니다.
        proxy_buffering off;

        # 같은 파일에 형식을 섞지 않도록 따로 씁니다. 통화 접속만 모여 보기도 편합니다.
        access_log /var/log/nginx/ws_access.log komit_ws;
    }

    # proxy_pass 끝의 /가 /service/ 접두사를 떼고 Spring에 넘깁니다.
    location /service/ {
        proxy_pass http://komit_service/;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Prefix /service;
    }
}
```

#### mock용 FE(`fe-test`)

`fe-test`는 NodePort `30083`으로 열려 있고, 위 `komit_test` upstream이 그 주소입니다.

이 FE는 `/test` 접두사로 구운 별도 이미지(`komit-fe-test`)입니다. Next.js의 `basePath`가
`/test`라서 nginx는 **접두사를 떼지 말고 그대로 넘겨야** 합니다.

```nginx
location /test {
    proxy_pass http://komit_test;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

두 가지를 지켜야 합니다.

- `proxy_pass` 끝에 `/`를 붙이지 않습니다. 붙이면 접두사가 떨어져 나가 Next가 `/`로 받고,
  화면이 만드는 링크와 API 호출이 실제 BE를 보는 기본 `fe`로 갑니다.
- **`location = /test { return 301 /test/; }`를 두지 않습니다.** `basePath`가 `/test`면
  Next는 `/test`를 정식 주소로 보고 `/test/`를 `/test`로 되돌립니다. nginx가 반대로
  되돌리면 두 리다이렉트가 맞물려 `ERR_TOO_MANY_REDIRECTS`가 납니다.

카카오 Redirect URI는 `https://komit.site/test/api/auth/kakao/callback`입니다. 카카오
콘솔에 등록하고 같은 값을 `TEST_KAKAO_REDIRECT_URI`에 넣어야 로그인이 됩니다.

문법 검사가 통과한 뒤에만 재시작합니다. 설정이 틀린 채로 재시작하면 nginx가 뜨지 않아 mock까지 접속이 끊깁니다.

```bash
docker compose run --rm --no-deps nginx nginx -t
docker compose up -d nginx
```

#### WebSocket 경로 확인

이 확인은 `service` Pod가 떠 있어야 뜻이 있습니다. 아직 첫 배포 전이면 **502**가 나오는 것이
정상이므로, 5단계를 마친 뒤 돌립니다.

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -H 'Connection: Upgrade' \
  -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' \
  -H "Sec-WebSocket-Key: $(head -c 16 /dev/urandom | base64)" \
  https://komit.site/service/ws/realtime
```

`401`이면 통과입니다. 티켓 없이 열려고 했으니 거절당하는 것이 맞고, 거절했다는 것은 요청이
프록시를 지나 Spring의 입장 검사까지 닿았다는 뜻입니다. `404`나 `200`이 나오면 `/service/ws/`
블록이 없거나 `/service/`에 걸려 업그레이드가 지워진 것입니다.

브라우저가 쓰는 주소는 `wss://komit.site/service/ws/realtime?ticket=...`입니다. `/service`
접두사는 프록시가 떼므로 Spring에는 `/ws/realtime`으로 들어갑니다.

### 5. 첫 배포

#### 첫 배포를 따로 챙기는 이유

`deploy.sh`는 이미지를 넘겨받지 못한 모듈은 **클러스터에서 돌고 있는 이미지를 그대로 씁니다.** 그런데 첫 배포 때는 클러스터가 비어 있어 가져올 이미지가 없습니다. 그래서 첫 배포 한 번은 `service`와 `escrow` 이미지를 **둘 다** 넘겨야 합니다.

CI는 이번 커밋에서 바뀐 모듈의 이미지만 넘기므로, 두 모듈이 모두 바뀐 커밋이 develop에 들어가야 CI로 첫 배포가 됩니다. K3s 배포 설정을 추가한 이 브랜치는 `service/`와 `escrow/`를 함께 바꾸고 있어서, **이 브랜치를 develop에 병합하는 것이 곧 첫 배포**입니다.

두 가지 방법이 있습니다. 보통은 A로 하고, CI가 막히면 B로 합니다.

---

#### 방법 A. CI로 첫 배포 (권장)

**A-0. 사전 점검 (서버)**

K3s가 nginx 컨테이너용 주소에도 NodePort를 열었는지 확인합니다.

```bash
sudo systemctl cat k3s | grep nodeport-addresses
```

`127.0.0.1/32,172.17.0.1/32`가 보이면 통과입니다. `127.0.0.1/32`만 있으면 nginx 컨테이너가 30080에 닿지 못해 **504**가 납니다. 1단계의 설치 명령을 새 옵션으로 **한 번 더 실행**하면 설정만 바뀌고 데이터는 유지됩니다.

CI가 사용할 계정이 비밀번호 없이 sudo를 쓸 수 있는지 확인합니다.

```bash
sudo -n true && echo OK
```

`OK`가 나오지 않으면 CI가 `deploy.sh`를 실행할 수 없습니다. `sudo -n -l`로 허용 범위를 확인합니다. docker 명령에만 허용되어 있다면 서버 관리자에게 권한 추가를 요청합니다.

**A-1. GitLab에 DB·Redis 비밀번호 등록**

이 비밀번호로 첫 배포 때 DB 계정과 Redis 비밀번호가 만들어집니다.

1. 비밀번호를 만듭니다. 영문·숫자만 쓰면 GitLab Masked 조건에 걸리지 않습니다.
   ```bash
   openssl rand -hex 24
   ```
   세 번 실행해 service DB용, escrow DB용, service Redis용을 따로 만듭니다.
2. GitLab 프로젝트 → **Settings → CI/CD → Variables → Add variable**
3. 아래 세 개를 등록합니다.

   | Key | Value | Visibility | Protect variable |
   |---|---|---|---|
   | `SERVICE_DB_PASSWORD` | service DB용 비밀번호 | **Masked** | 체크 |
   | `ESCROW_DB_PASSWORD` | escrow DB용 비밀번호 | **Masked** | 체크 |
   | `SERVICE_REDIS_PASSWORD` | service Redis용 비밀번호 | **Masked** | 체크 |

4. **Settings → Repository → Protected branches**에 `develop`이 있는지 확인합니다. Protected 변수는 보호 브랜치 파이프라인에서만 읽힙니다. `develop`이 없으면 변수가 빈 값으로 들어가 배포 잡이 실패합니다.

DB 비밀번호는 한 번 정하면 바꾸지 않습니다. DB 계정은 첫 배포 때 만들어지므로, 나중에 변수 값만 바꾸면 Spring과 DB의 비밀번호가 어긋날 수 있습니다. Redis 비밀번호는 배포마다 Secret과 redis.conf에 함께 반영되지만, 바꾸면 Redis Pod가 재시작됩니다.

비밀이 아닌 주소 변수도 함께 등록합니다. 백엔드가 자기 공개 주소를 알아야 리다이렉트와 절대 URL을 `komit.site/service/...`로 만들 수 있습니다.

| Key | Value | Visibility |
|---|---|---|
| `SERVICE_PUBLIC_BASE_URL` | `https://komit.site/service` | Visible |
| `ESCROW_PUBLIC_BASE_URL` | `https://komit.site/escrow` | Visible |
| `BACKEND_API_BASE_URL` | `https://komit.site/service` | Visible |
| `TEST_BACKEND_API_BASE_URL` | `https://komit.site/mock/service` | Visible |
| `TEST_AI_API_BASE_URL` | `https://komit.site/mock/service` | Visible |
| `KAKAO_REDIRECT_URI` | `https://komit.site/api/auth/kakao/callback` | Masked |
| `TEST_KAKAO_REDIRECT_URI` | `https://komit.site/test/api/auth/kakao/callback` | Masked |

`AI_API_BASE_URL`은 실제 AI 배포 주소를 넣습니다. 루트 FE 변수에는 mock 주소를 넣지 않고,
테스트 FE의 mock 주소는 반드시 `TEST_*` 변수에만 둡니다.

`SSH_ADDRESS`, `SSH_PRIVATE_KEY`, `DOCKER_REGISTRY*`는 mock 배포에서 이미 쓰고 있어 추가하지 않습니다.

**A-2. develop에 병합**

1. 이 브랜치의 변경을 커밋·푸시합니다.
2. 병합 전에 두 모듈이 모두 바뀌었는지 확인합니다.
   ```bash
   git diff --stat origin/develop...HEAD -- service escrow
   ```
   `service/`와 `escrow/` 파일이 둘 다 보여야 합니다.
3. GitLab에서 develop 대상으로 MR을 만들고, MR 파이프라인(규칙 검사·빌드·테스트·소나)이 통과하면 병합합니다.

**A-3. 배포 파이프라인 지켜보기**

병합하면 develop 파이프라인이 새로 돕니다. GitLab 프로젝트 → **Build → Pipelines**에서 develop 파이프라인을 엽니다.

```text
deploy 스테이지
 ├─ service-docker-build-push   이미지 빌드·푸시 (몇 분)
 ├─ escrow-docker-build-push    이미지 빌드·푸시 (몇 분)
 └─ k3s-deploy                  위 두 잡이 끝나면 시작
```

`k3s-deploy` 잡을 눌러 로그를 봅니다. 순서대로 아래 줄이 나오면 정상입니다. **첫 배포는 10분 안팎** 걸립니다. PostgreSQL 이미지를 처음 받고 DB 인스턴스 6개를 하나씩 만들기 때문입니다.

```text
service image: docker.io/<계정>/komit-service:<커밋>      ← 둘 다 "(유지)"가 아니어야 함
escrow image:  docker.io/<계정>/komit-escrow:<커밋>
helmchart.helm.cattle.io/cnpg created                      ← 1. 오퍼레이터 설치
deployment.apps/cnpg-cloudnative-pg condition met
helmchart.helm.cattle.io/redis-operator created            ← 1-1. Redis 오퍼레이터 설치
deployment.apps/redis-operator condition met
cluster.postgresql.cnpg.io/service-db created              ← 2. DB·Redis 생성
cluster.postgresql.cnpg.io/service-db condition met           (여기서 몇 분 멈춰 있어도 정상)
cluster.postgresql.cnpg.io/escrow-db condition met
statefulset rolling update complete 3 pods ...service-redis
statefulset rolling update complete 3 pods ...service-redis-sentinel
deployment.apps/service created                            ← 3. 앱 배포
deployment "service" successfully rolled out
deployment "escrow" successfully rolled out
```

잡이 초록색(passed)으로 끝나면 배포가 끝난 것입니다.

**A-4. 서버에서 상태 확인**

```bash
sudo kubectl -n komit get cluster
```

```text
NAME         AGE   INSTANCES   READY   STATUS                     PRIMARY
escrow-db    8m    3           3       Cluster in healthy state   escrow-db-1
service-db   8m    3           3       Cluster in healthy state   service-db-1
```

```bash
sudo kubectl -n komit get redisreplication,redissentinel
sudo kubectl -n komit get pods -L role,redis-role
```

`service-…` 2개, `escrow-…` 2개, `service-db-1~3`, `escrow-db-1~3`, `service-redis-0~2`, `service-redis-sentinel-0~2`가 모두 `Running`, `READY 1/1`이고 `service-redis-…` 중 하나가 `redis-role=master`이면 정상입니다.

**A-5. 외부 접속 확인**

```bash
curl https://komit.site/service/actuator/health
```

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

```bash
curl https://komit.site/escrow/actuator/health
```

같은 `UP` 응답이 나와야 합니다.

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://komit.site/
```

FE가 떠 있으면 `200`이 나옵니다.

---

#### 이미 돌고 있는 클러스터에 FE를 처음 추가할 때

`deploy.sh`는 클러스터에 `fe` 또는 `fe-test` Deployment가 없으면 각각 `FE_IMAGE`,
`FE_TEST_IMAGE`를 반드시 받아야 합니다. 그런데 `deploy/k3s/`만 바뀐 병합에서는 FE 이미지
빌드 잡이 돌지 않아 값이 비어 있고 `k3s-deploy`가 실패할 수 있습니다.

```text
fe image is required because deployment/fe does not exist yet
fe-test image is required because deployment/fe-test does not exist yet
```

이 경우 한 번만 서버에서 직접 실행합니다. `k3s-deploy`는 실패하기 전에 새 `deploy/k3s`를 서버의 `~/komit-k3s/k3s`에 이미 올려 두었으므로 그 파일을 씁니다.

1. Docker Hub의 `komit-fe`, `komit-fe-test` 저장소 → **Tags**에서 최신 커밋 태그를 확인합니다.
2. 서버에서 실행합니다. service·escrow 이미지는 넘기지 않아 지금 돌고 있는 이미지가 유지됩니다.

   ```bash
   read -rsp "SERVICE_DB_PASSWORD: " SERVICE_DB_PASSWORD; echo
   read -rsp "ESCROW_DB_PASSWORD: " ESCROW_DB_PASSWORD; echo
   read -rsp "SERVICE_REDIS_PASSWORD: " SERVICE_REDIS_PASSWORD; echo
   export SERVICE_DB_PASSWORD ESCROW_DB_PASSWORD SERVICE_REDIS_PASSWORD
   export FE_IMAGE=docker.io/<계정>/komit-fe:<태그>
   export FE_TEST_IMAGE=docker.io/<계정>/komit-fe-test:<태그>
   # GitLab CI/CD 변수에 등록된 값과 같게 넣습니다.
   export SERVICE_PUBLIC_BASE_URL=https://komit.site/service
   export ESCROW_PUBLIC_BASE_URL=https://komit.site/escrow
   export BACKEND_API_BASE_URL=<GitLab 변수 값>
   export AI_API_BASE_URL=<GitLab 변수 값>
   export TEST_BACKEND_API_BASE_URL=<GitLab 변수 값>
   export TEST_AI_API_BASE_URL=<GitLab 변수 값>
   export KAKAO_CLIENT_ID=<GitLab 변수 값>
   export KAKAO_REDIRECT_URI=<GitLab 변수 값>
   export TEST_KAKAO_REDIRECT_URI=<GitLab 변수 값>

   sudo --preserve-env=SERVICE_DB_PASSWORD,ESCROW_DB_PASSWORD,SERVICE_REDIS_PASSWORD,FE_IMAGE,FE_TEST_IMAGE,SERVICE_PUBLIC_BASE_URL,ESCROW_PUBLIC_BASE_URL,BACKEND_API_BASE_URL,AI_API_BASE_URL,TEST_BACKEND_API_BASE_URL,TEST_AI_API_BASE_URL,KAKAO_CLIENT_ID,KAKAO_REDIRECT_URI,TEST_KAKAO_REDIRECT_URI \
     sh ~/komit-k3s/k3s/deploy.sh
   ```

3. `sudo kubectl -n komit get pods -l app.kubernetes.io/name=fe`로 `fe-…` Pod 2개가 `Running`인지 확인합니다.

이후에는 `fe/`가 바뀐 병합마다 CI가 `FE_IMAGE`를 넘겨 자동으로 배포합니다.

---

#### 방법 B. 서버에서 직접 첫 배포

CI 배포 잡이 막혔을 때 씁니다. Docker Hub에 이미 올라간 이미지로 배포합니다.

**B-1. 배포할 이미지 태그 확인**

Docker Hub의 `komit-service`, `komit-escrow` 저장소 → **Tags**에서 develop 최신 커밋 태그(짧은 커밋 해시)를 확인합니다. `latest`는 쓰지 않습니다.

service·escrow의 Dockerfile과 이미지 빌드 잡은 K3s 배포 설정과 함께 develop에 들어갑니다. 그래서 **방법 A의 병합 전에는 두 이미지가 Docker Hub에 없습니다.** 병합 후 이미지 빌드 잡은 성공했는데 `k3s-deploy`만 실패한 경우에 이 방법을 씁니다.

**B-2. 배포 파일을 서버로 복사 (로컬 PC)**

저장소 루트에서 실행합니다.

```bash
scp -r deploy/k3s <계정>@komit.site:~/komit-k3s-manual
```

**B-3. 서버에서 실행**

비밀번호는 명령어에 직접 쓰지 않고 입력받습니다. 명령어에 쓰면 셸 기록(`~/.bash_history`)에 남습니다. **A-1에서 GitLab에 등록한 값과 같은 비밀번호**를 입력해야 이후 CI 배포와 어긋나지 않습니다.

```bash
read -rsp "SERVICE_DB_PASSWORD: " SERVICE_DB_PASSWORD; echo
read -rsp "ESCROW_DB_PASSWORD: " ESCROW_DB_PASSWORD; echo
read -rsp "SERVICE_REDIS_PASSWORD: " SERVICE_REDIS_PASSWORD; echo
export SERVICE_DB_PASSWORD ESCROW_DB_PASSWORD SERVICE_REDIS_PASSWORD

export SERVICE_IMAGE=docker.io/<계정>/komit-service:<태그>
export ESCROW_IMAGE=docker.io/<계정>/komit-escrow:<태그>
export FE_IMAGE=docker.io/<계정>/komit-fe:<태그>
export FE_TEST_IMAGE=docker.io/<계정>/komit-fe-test:<태그>

# GitLab CI/CD 변수에 등록된 값과 같게 넣습니다.
export SERVICE_PUBLIC_BASE_URL=https://komit.site/service
export ESCROW_PUBLIC_BASE_URL=https://komit.site/escrow
export BACKEND_API_BASE_URL=<GitLab 변수 값>
export AI_API_BASE_URL=<GitLab 변수 값>
export TEST_BACKEND_API_BASE_URL=<GitLab 변수 값>
export TEST_AI_API_BASE_URL=<GitLab 변수 값>
export KAKAO_CLIENT_ID=<GitLab 변수 값>
export KAKAO_REDIRECT_URI=<GitLab 변수 값>
export TEST_KAKAO_REDIRECT_URI=<GitLab 변수 값>

sudo --preserve-env=SERVICE_DB_PASSWORD,ESCROW_DB_PASSWORD,SERVICE_REDIS_PASSWORD,SERVICE_IMAGE,ESCROW_IMAGE,FE_IMAGE,FE_TEST_IMAGE,SERVICE_PUBLIC_BASE_URL,ESCROW_PUBLIC_BASE_URL,BACKEND_API_BASE_URL,AI_API_BASE_URL,TEST_BACKEND_API_BASE_URL,TEST_AI_API_BASE_URL,KAKAO_CLIENT_ID,KAKAO_REDIRECT_URI,TEST_KAKAO_REDIRECT_URI \
  sh ~/komit-k3s-manual/deploy.sh
```

출력과 확인 방법은 A-3 ~ A-5와 같습니다. 끝나면 복사한 파일을 지웁니다.

```bash
rm -rf ~/komit-k3s-manual
```

---

#### 잘 안 될 때

| 증상 | 원인 | 확인·해결 |
|---|---|---|
| 잡 로그: `SERVICE_DB_PASSWORD CI/CD variable is required` | 변수 미등록, 또는 `develop`이 보호 브랜치가 아님 | A-1의 3~4번 확인 |
| 잡 로그: `sudo: a password is required` | 배포 계정 sudo 권한 부족 | A-0의 `sudo -n -l` 확인 |
| 잡 로그: `service image is required because deployment/service does not exist yet` | 한쪽 모듈만 빌드됨 | A-2의 `git diff` 확인, 또는 방법 B |
| 잡 로그: `fe image is required because deployment/fe does not exist yet` | FE를 처음 추가하는데 FE 이미지가 빌드되지 않음 | 「이미 돌고 있는 클러스터에 FE를 처음 추가할 때」 |
| `fe` Pod는 Running인데 페이지에서 API 호출이 실패 | FE 서버가 백엔드에 닿지 못함 | `sudo kubectl -n komit logs deploy/fe`. `sudo kubectl -n komit exec deploy/fe -- env \| grep API_BASE_URL`로 GitLab 변수 값이 들어갔는지 확인 |
| 잡 로그: `BACKEND_API_BASE_URL CI/CD variable is required` | FE 주소 변수 미등록 | GitLab 변수 `BACKEND_API_BASE_URL`, `AI_API_BASE_URL` 확인 |
| `cluster ... condition met`에서 10분 넘게 멈춤 | DB Pod가 못 뜸 | `sudo kubectl -n komit get pods`로 DB Pod 상태 확인. `Pending`이면 메모리 부족, `ImagePullBackOff`이면 서버의 인터넷(ghcr.io) 접속 확인 |
| `komit-service` Pod가 `ImagePullBackOff` | Docker Hub 저장소가 private | 3단계 Docker Hub 인증 |
| 앱 Pod가 `CrashLoopBackOff` | 앱 기동 실패 | `sudo kubectl -n komit logs deploy/service`. `password authentication failed`이면 비밀번호가 DB와 다름 |
| `statefulset rolling update complete`가 `service-redis`에서 멈춤 | Redis Pod가 못 뜸 | `sudo kubectl -n komit describe pod service-redis-0`. PVC가 `Pending`이면 스토리지, init 컨테이너 실패면 `service-redis-config` ConfigMap 문법 확인 |
| `service-redis-sentinel` Pod가 `CrashLoopBackOff` | Sentinel이 Primary를 못 찾음 | `sudo kubectl -n komit get redisreplication service-redis`의 `MASTER` 열이 비어 있으면 `sudo kubectl -n ot-operators logs deploy/redis-operator` 확인 |
| 앱 로그에 `NOAUTH` 또는 `WRONGPASS` | Spring과 Redis 비밀번호가 다름 | `service-redis-auth` Secret과 `SERVICE_REDIS_PASSWORD` 값 확인 |
| `curl`이 **502** | 30080에 받는 쪽이 없음 | `sudo kubectl -n komit get pods`로 service Pod가 Ready인지 확인 |
| `curl`이 **504** (약 60초 뒤) | nginx 컨테이너가 30080에 닿지 못함 | A-0의 `nodeport-addresses` 확인 |
| 리다이렉트 `Location`이 `https://komit.site/login`처럼 `/service` 접두사를 잃음 | nginx의 `X-Forwarded-Prefix`가 빠졌거나 Spring이 헤더를 반영하지 않음 | 서버 nginx `location /service/`에 `proxy_set_header X-Forwarded-Prefix /service;`가 있는지 확인. Spring은 prod에서 `server.forward-headers-strategy=framework`로 반영합니다 |
| WebSocket 핸드셰이크가 **404**나 **200**으로 끝남 | `/service/ws/` 블록이 없어 `/service/`에 걸렸고, 그쪽이 `Connection ""`으로 업그레이드를 지움 | 4단계의 `location /service/ws/`와 `map $http_upgrade` 블록이 들어갔는지 확인 |
| 통화가 **1분쯤 지나 저절로** 끊김 | `proxy_read_timeout` 기본값 60초 | `location /service/ws/`의 `proxy_read_timeout`·`proxy_send_timeout` 확인 |
| WebSocket 핸드셰이크가 **403** | Spring의 같은 출처 검사에 막힘. 프록시가 원래 scheme·host를 넘기지 않음 | `X-Forwarded-Proto`, `Host` 헤더와 prod의 `server.forward-headers-strategy=framework` 확인. 다른 출처에서 열어야 하면 `app.realtime.allowed-origins`에 추가 |
| 통화 신호가 눈에 띄게 느림 | 프록시가 모아서 보냄 | `location /service/ws/`의 `proxy_buffering off` 확인 |
| 앱 로그: `Binding to target ... app.public-base-url` 또는 `APP_PUBLIC_BASE_URL` 관련 기동 실패 | 공개 주소 변수 미등록 | GitLab 변수 `SERVICE_PUBLIC_BASE_URL`, `ESCROW_PUBLIC_BASE_URL` 확인 |

## CI/CD

`develop`에 `service/`, `escrow/`, `fe/`, `deploy/k3s/` 변경이 병합되면 `k3s-deploy` 잡이 돕니다.

```text
service 변경 → service-docker-build-push ─┐ SERVICE_IMAGE (dotenv)
escrow 변경  → escrow-docker-build-push  ─┤ ESCROW_IMAGE  (dotenv)
fe 변경      → fe-docker-build-push      ─┤ FE_IMAGE      (dotenv)
             → fe-test-docker-build-push ─┤ FE_TEST_IMAGE (dotenv)
                                          ▼
                                    k3s-deploy
                                    1. deploy/k3s를 서버 ~/komit-k3s로 복사
                                    2. 표준입력으로 비밀번호·이미지를 넘겨 deploy.sh 실행
```

| CI/CD 변수 | 설정 | 용도 |
|---|---|---|
| `SSH_ADDRESS`, `SSH_PRIVATE_KEY` | 목 배포와 공용 | 서버 접속 |
| `SERVICE_DB_PASSWORD`, `ESCROW_DB_PASSWORD` | Masked, Protected | DB 계정 Secret |
| `SERVICE_REDIS_PASSWORD` | Masked, Protected | Redis·Sentinel 비밀번호 Secret(`service-redis-auth`) |
| `SERVICE_PUBLIC_BASE_URL`, `ESCROW_PUBLIC_BASE_URL` | Visible | 각 백엔드의 공개 주소(`https://komit.site/service`, `https://komit.site/escrow`). `service-config`, `escrow-config` ConfigMap의 `APP_PUBLIC_BASE_URL`이 됩니다 |
| `BACKEND_API_BASE_URL`, `AI_API_BASE_URL` | Visible, Protected | `komit.site` 기본 FE가 호출할 실제 BE·AI 주소. `fe-config`에만 들어갑니다 |
| `TEST_BACKEND_API_BASE_URL`, `TEST_AI_API_BASE_URL` | Visible, Protected | `komit.site/test` FE가 호출할 mock 주소. `fe-test-config`에만 들어갑니다 |
| `KAKAO_CLIENT_ID`, `KAKAO_REDIRECT_URI` | Masked | 카카오 로그인. fe 실행 환경(`fe-config`). Redirect URI 는 카카오 콘솔에 등록한 배포 주소(`https://komit.site/api/auth/kakao/callback`)와 글자까지 같아야 합니다 |
| `ACCOUNT_DATA_ENCRYPTION_KEY` | Masked, Protected | 계좌번호와 `userKey`를 암호화해 보관하는 AES-256 키. 정확히 32바이트인 키의 Base64 값이어야 합니다. `service-auth` Secret으로 주입되며, 없으면 service Pod가 기동 중 죽습니다. **한 번 정한 뒤 바꾸면 이미 저장된 암호문을 읽지 못합니다** |
| `TEST_KAKAO_REDIRECT_URI` | Masked | `fe-test`의 카카오 Redirect URI(`https://komit.site/test/api/auth/kakao/callback`). 루트 주소와 따로 카카오 콘솔에 등록합니다 |
| `CLIENT_API_KEY_HMAC_SECRET`, `SENSITIVE_DATA_HMAC_SECRET` | Masked, Protected | Escrow 내부 API 키와 민감정보 조회값을 위한 HMAC 비밀키. 각각 32자 이상이어야 합니다 |
| `FIELD_ENCRYPTION_KEY_BASE64` | Masked, Protected | Escrow 복구용 민감정보 AES-256 키. 정확히 32바이트인 키의 Base64 값이어야 합니다 |
| `OPERATOR_CLIENT_API_KEY` | Masked, Protected | Escrow 운영자 API 키. 32자 이상이어야 합니다 |
| `ESCROW_API_BASE_URL` | Visible, Protected | Service가 호출할 실제 Escrow 주소. k3s에서는 `http://escrow.komit.svc.cluster.local:8081`을 사용합니다. 클라이언트가 `/api/v1/...`를 붙이므로 `/api`를 중복해서 넣지 않습니다 |
| `ESCROW_CLIENT_API_KEY` | Masked, Protected | 현재 Mock은 명세 고정값 `mock-client-api-key`를 사용합니다. 실제 Escrow 전환 시에는 32자 이상의 키를 사용하며 HMAC 해시만 `service_clients`에 등록됩니다 |
| `ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE` | Visible, Protected | 해당 API 키의 `service_clients.one_won_auth_message`에 저장할 1원 인증 문구. 현재 권장값은 `KOMIT`입니다 |
| `ESCROW_ADMIN_USERNAME` | Visible, Protected | Escrow 조회 전용 운영 페이지 로그인 아이디 |
| `ESCROW_ADMIN_PASSWORD_HASH` | Masked, Protected | 운영 페이지 비밀번호의 `{bcrypt}...` 해시. 원문 비밀번호는 저장하지 않습니다 |
| `SSAFY_FINANCE_API_KEY` | Masked, Protected | Escrow가 SSAFY 교육용 금융망을 호출할 때 사용하는 앱 관리자 API Key |
| `SSAFY_DEMAND_DEPOSIT_PRODUCT_NO` | Visible, Protected | R-ACC-10에 따라 해당 SSAFY 환경에 미리 등록한 입출금 상품 고유번호 |
| `SSAFY_ESCROW_USER_KEY`, `SSAFY_ESCROW_ACCOUNT_NO` | Masked, Protected | 계약별 가상계좌의 모계좌가 되는 Escrow 보관 주체의 금융망 사용자 키와 16자리 보관계좌 번호 |
| `SSAFY_FEE_ACCOUNT_NO` | Masked, Protected | 보관계좌와 같은 Escrow 사용자 명의로 개설한 별도 16자리 플랫폼 수수료 수익 계좌 번호 |
| `SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS` | Visible, Protected | 가상계좌 만료일 계산에 사용할 1~30일. SSAFY 금융망은 발급일로부터 30일 이내만 허용한다 |

> GitLab에 CI/CD 변수를 등록하는 것만으로는 Pod에 전달되지 않습니다. 애플리케이션에 운영 환경변수를
> 추가할 때는 같은 MR에서 `.gitlab-ci.yml`의 필수 변수 검사와 SSH 전달 목록, `deploy/k3s/deploy.sh`의
> 입력 검증 및 Secret/ConfigMap 생성, Deployment의 `envFrom` 또는 `env` 연결을 모두 확인해야 합니다.
> 민감값은 `secretGenerator`, 공개 설정값은 `configMapGenerator`를 사용합니다. 새 설정으로 배포를
> 검증할 때는 과거 Job을 Retry하지 말고 해당 변경이 포함된 새 파이프라인을 실행합니다.

- 이번 파이프라인에서 빌드되지 않은 모듈은 서버에서 돌고 있는 이미지를 유지합니다. `deploy/k3s/`만 바뀌면 이미지 교체 없이 설정만 반영합니다.
- 비밀번호는 SSH 명령줄 인자가 아니라 표준입력으로 전달해 서버 프로세스 목록에 남기지 않습니다.
- `resource_group`으로 배포가 동시에 두 번 돌지 않게 합니다.

## 수동 배포

다음 환경변수를 배포 서버나 CI의 보호된 변수로 주입합니다. 비밀번호를 저장소 파일에 기록하지 마세요.

```bash
export SERVICE_DB_PASSWORD=<password>
export ESCROW_DB_PASSWORD=<password>
export SERVICE_REDIS_PASSWORD=<password>
export SERVICE_PUBLIC_BASE_URL=https://komit.site/service
export ESCROW_PUBLIC_BASE_URL=https://komit.site/escrow
export BACKEND_API_BASE_URL=<fe가 호출할 백엔드 주소>
export AI_API_BASE_URL=<fe가 호출할 AI 주소>
export TEST_BACKEND_API_BASE_URL=<fe-test가 호출할 mock service 주소>
export TEST_AI_API_BASE_URL=<fe-test가 호출할 mock AI 주소>
export KAKAO_CLIENT_ID=<카카오 REST API 키>
export KAKAO_REDIRECT_URI=<카카오 콜백 주소>
export TEST_KAKAO_REDIRECT_URI=https://komit.site/test/api/auth/kakao/callback
export CLIENT_API_KEY_HMAC_SECRET=<32자 이상 비밀값>
export SENSITIVE_DATA_HMAC_SECRET=<32자 이상 비밀값>
export FIELD_ENCRYPTION_KEY_BASE64=<32바이트 AES 키의 Base64 값>
export OPERATOR_CLIENT_API_KEY=<32자 이상 운영자 키>
export SSAFY_FINANCE_API_KEY=<SSAFY 금융망 앱 관리자 API Key>
export SSAFY_DEMAND_DEPOSIT_PRODUCT_NO=<SSAFY 입출금 상품 고유번호>
export SSAFY_ESCROW_USER_KEY=<Escrow 보관 주체의 SSAFY userKey>
export SSAFY_ESCROW_ACCOUNT_NO=<Escrow 보관계좌 16자리 번호>
export SSAFY_FEE_ACCOUNT_NO=<Escrow 플랫폼 수수료 계좌 16자리 번호>
export SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS=<가상계좌 유효 일수>

# 이번에 바뀐 모듈만 넘깁니다. 클러스터에 아직 없는 모듈은 반드시 넘깁니다.
export SERVICE_IMAGE=docker.io/<account>/komit-service:<commit-sha>
export ESCROW_IMAGE=docker.io/<account>/komit-escrow:<commit-sha>
export FE_IMAGE=docker.io/<account>/komit-fe:<commit-sha>
export FE_TEST_IMAGE=docker.io/<account>/komit-fe-test:<commit-sha>

chmod +x deploy/k3s/deploy.sh
./deploy/k3s/deploy.sh
```

| 환경변수 | 필수 | 설명 |
|---|---|---|
| `SERVICE_DB_PASSWORD`, `ESCROW_DB_PASSWORD` | 예 | DB 소유자 계정 비밀번호. `service-db-app`, `escrow-db-app` Secret이 되어 CloudNativePG initdb와 Spring이 함께 읽습니다. |
| `SERVICE_REDIS_PASSWORD` | 예 | Redis `requirepass`·`masterauth`와 Sentinel 접속 비밀번호. `service-redis-auth` Secret이 되어 오퍼레이터와 Spring이 함께 읽습니다. |
| `SERVICE_PUBLIC_BASE_URL`, `ESCROW_PUBLIC_BASE_URL` | 예 | 각 백엔드가 밖에서 보이는 주소(nginx 경로 접두사 포함). Spring의 `app.public-base-url`이 되고, 없으면 앱이 기동하지 않습니다. 값이 바뀌면 해당 Pod만 다시 뜹니다. |
| `BACKEND_API_BASE_URL`, `AI_API_BASE_URL` | 예 | 기본 `fe`가 호출할 실제 BE·AI 주소. GitLab CI/CD 변수와 같은 값을 넣으며 `fe-config`에만 들어갑니다. |
| `TEST_BACKEND_API_BASE_URL`, `TEST_AI_API_BASE_URL` | 예 | `/test`의 `fe-test`가 호출할 mock 주소. `fe-test-config`에만 들어갑니다. |
| `ESCROW_API_BASE_URL` | 예 | GitLab CI/CD 값이 `service-config`에 주입됩니다. `http://escrow.komit.svc.cluster.local:8081`로 설정해 실제 Escrow를 사용합니다. |
| `KAKAO_CLIENT_ID`, `KAKAO_REDIRECT_URI` | 예 | 카카오 로그인 REST API 키와 콜백 주소. fe 의 `/api/auth/kakao` 가 실행 시점에 읽습니다. 없으면 로그인만 500 으로 실패하고 다른 화면은 영향이 없습니다. |
| `ACCOUNT_DATA_ENCRYPTION_KEY` | 예 | 계좌 데이터 AES-256 암호화 키. Base64 디코딩 결과가 정확히 32바이트여야 하며 `service-auth` Secret으로 주입됩니다. 이 Secret은 `secretGenerator`가 만들어 이름에 해시가 붙으므로, 값이 바뀌면 service Pod가 새 값으로 다시 뜹니다. 값을 바꾸면 기존 암호문을 복호화할 수 없습니다. |
| `TEST_KAKAO_REDIRECT_URI` | 예 | `/test` 전용 콜백 주소. `https://komit.site/test/api/auth/kakao/callback`을 카카오 콘솔에도 등록해야 합니다. |
| `CLIENT_API_KEY_HMAC_SECRET`, `SENSITIVE_DATA_HMAC_SECRET` | 예 | Escrow HMAC 비밀키. 각각 32자 이상이며 `escrow-security` Secret으로 주입됩니다. |
| `FIELD_ENCRYPTION_KEY_BASE64` | 예 | Escrow AES-256 암호화 키. Base64 디코딩 결과가 정확히 32바이트여야 합니다. |
| `OPERATOR_CLIENT_API_KEY` | 예 | Escrow 운영자 API 키. 32자 이상이며 `escrow-security` Secret으로 주입됩니다. |
| `SSAFY_FINANCE_API_KEY` | 예 | SSAFY 금융망 앱 관리자 API Key. `escrow-security` Secret으로 주입되며 저장소나 ConfigMap에 기록하지 않습니다. |
| `SSAFY_DEMAND_DEPOSIT_PRODUCT_NO` | 예 | R-ACC-10 입출금 상품 고유번호. `escrow-config` ConfigMap으로 주입됩니다. |
| `SSAFY_ESCROW_USER_KEY`, `SSAFY_ESCROW_ACCOUNT_NO` | 예 | Escrow 보관 주체의 금융망 사용자 키와 보관계좌 번호. `escrow-security` Secret으로 주입합니다. |
| `SSAFY_FEE_ACCOUNT_NO` | 예 | 보관계좌와 다른 플랫폼 수수료 수익 계좌 번호. `escrow-security` Secret으로 주입합니다. |
| `SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS` | 예 | 가상계좌 만료일 계산에 쓰는 1~30일. `escrow-config` ConfigMap으로 주입합니다. |
| `SERVICE_IMAGE`, `ESCROW_IMAGE`, `FE_IMAGE`, `FE_TEST_IMAGE` | 아니요 | 비어 있으면 클러스터에서 돌고 있는 이미지를 유지합니다. 바뀌지 않은 모듈은 재시작하지 않습니다. 클러스터에 아직 없는 모듈은 필수입니다. |

스크립트는 배포할 이미지 태그를 임시 디렉터리의 Kustomize overlay에 고정해 적용하고, 각 단계의 준비 완료를 기다린 뒤 다음 단계로 넘어갑니다. DB·Redis 주소는 클러스터 내부 DNS 이름으로 고정되어 있어 주입하지 않습니다.

## 확인

```bash
kubectl -n komit get cluster
kubectl -n komit get redisreplication,redissentinel
kubectl -n komit get pods -o wide -L role,redis-role

kubectl -n komit port-forward service/service 8080:8080
curl http://localhost:8080/actuator/health
```

두 FE가 올바른 서버를 보고 있는지는 주입된 주소로 봅니다. `fe`는 실제 BE 주소,
`fe-test`는 mock 주소가 나와야 합니다.

```bash
kubectl -n komit exec deploy/fe    -- env | grep BACKEND_API_BASE_URL
kubectl -n komit exec deploy/fe-test -- env | grep BACKEND_API_BASE_URL
```

WebSocket 경로는 프록시를 지나야 뜻이 있으므로 「4. nginx 연결」의 확인 명령으로 봅니다.

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
| Hibernate 스키마 생성(`ddl-auto`) | 쓰기 풀 → `-rw` (Primary) |

- `DB_READ_URL`이 없으면 쓰기 주소를 그대로 씁니다. `local` 프로필과 CI 테스트는 DB 하나로 동작합니다.
- 조회 메서드에 `readOnly = true`를 붙여야 Standby로 갑니다. 쓰기 직후 최신값이 반드시 필요한 조회는 `readOnly`를 빼서 Primary에서 읽습니다.

### 알려진 제약
- **단일 노드:** 운영 EC2가 한 대라 Primary와 Standby가 같은 노드와 디스크(local-path)에 있습니다. Pod 장애는 견디지만 서버나 디스크 장애에는 앱과 DB가 함께 멈추고, 백업이 없어 데이터도 복구할 수 없습니다.
- **Standby 전체 장애:** Standby가 모두 내려가 `-ro` Service에 대상이 없으면 `readOnly` 트랜잭션이 실패합니다. 쓰기 트랜잭션은 계속 동작합니다. Standby를 2대 이상 유지하는 이유입니다.
