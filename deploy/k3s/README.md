# K3s 배포

`service`, `escrow`, `fe`를 각각 2개의 Pod로 실행하고, 두 서버의 DB를 CloudNativePG 클러스터로 분리합니다. `fe`(Next.js 서버)는 클러스터 안에서 Service 이름으로 스프링을 호출합니다. Spring이 트랜잭션 성격에 따라 쓰기는 Primary로, `readOnly` 조회는 Standby로 보냅니다.

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
| `prod/` | 운영 overlay. `service`(30080), `escrow`(30081), `fe`(30082)를 nginx용 NodePort로 엽니다 |
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

## 운영 서버 준비 (최초 1회)

목 서버가 도는 EC2에 K3s를 함께 설치합니다. Docker와 K3s는 컨테이너 런타임이 분리되어 있어 같이 설치해도 됩니다.

```text
인터넷 ─ 80/443 ─ nginx 컨테이너 ─┬─ /mock/    ─ mock 컨테이너
                                  ├─ /service/ ─ 172.17.0.1:30080 ─ K3s service Pod
                                  ├─ /escrow/  ─ 172.17.0.1:30081 ─ K3s escrow Pod
                                  └─ /         ─ 172.17.0.1:30082 ─ K3s fe Pod ─ (클러스터 내부) ─ service Pod
```

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
- `nodeport-addresses`: `127.0.0.1`은 서버 자신, `172.17.0.1`은 nginx 컨테이너가 호스트로 들어오는 Docker 브리지 주소입니다. 이 두 주소에만 NodePort가 열려 인터넷에서 30080~30082로 직접 들어올 수 없습니다. `docker0` 주소가 다르면 그 값으로 바꿉니다.

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

문법 검사가 통과한 뒤에만 재시작합니다. 설정이 틀린 채로 재시작하면 nginx가 뜨지 않아 mock까지 접속이 끊깁니다.

```bash
docker compose run --rm --no-deps nginx nginx -t
docker compose up -d nginx
```

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

**A-1. GitLab에 DB 비밀번호 등록**

이 비밀번호로 첫 배포 때 DB 계정이 만들어집니다.

1. 비밀번호를 만듭니다. 영문·숫자만 쓰면 GitLab Masked 조건에 걸리지 않습니다.
   ```bash
   openssl rand -hex 24
   ```
   두 번 실행해 service용, escrow용을 따로 만듭니다.
2. GitLab 프로젝트 → **Settings → CI/CD → Variables → Add variable**
3. 아래 두 개를 등록합니다.

   | Key | Value | Visibility | Protect variable |
   |---|---|---|---|
   | `SERVICE_DB_PASSWORD` | service용 비밀번호 | **Masked** | 체크 |
   | `ESCROW_DB_PASSWORD` | escrow용 비밀번호 | **Masked** | 체크 |

4. **Settings → Repository → Protected branches**에 `develop`이 있는지 확인합니다. Protected 변수는 보호 브랜치 파이프라인에서만 읽힙니다. `develop`이 없으면 변수가 빈 값으로 들어가 배포 잡이 실패합니다.

비밀번호는 한 번 정하면 바꾸지 않습니다. DB 계정은 첫 배포 때 만들어지므로, 나중에 변수 값만 바꾸면 Spring과 DB의 비밀번호가 어긋날 수 있습니다.

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
cluster.postgresql.cnpg.io/service-db created              ← 2. DB 생성
cluster.postgresql.cnpg.io/service-db condition met           (여기서 몇 분 멈춰 있어도 정상)
cluster.postgresql.cnpg.io/escrow-db condition met
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
sudo kubectl -n komit get pods -L role
```

`service-…` 2개, `escrow-…` 2개, `service-db-1~3`, `escrow-db-1~3`이 모두 `Running`, `READY 1/1`이면 정상입니다.

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

`deploy.sh`는 클러스터에 `fe` Deployment가 없으면 `FE_IMAGE`를 반드시 받아야 합니다. 그런데 `deploy/k3s/`만 바뀐 병합에서는 FE 이미지 빌드 잡이 돌지 않아 `FE_IMAGE`가 비어 있고, `k3s-deploy`가 아래 메시지로 실패합니다.

```text
fe image is required because deployment/fe does not exist yet
```

이 경우 한 번만 서버에서 직접 실행합니다. `k3s-deploy`는 실패하기 전에 새 `deploy/k3s`를 서버의 `~/komit-k3s/k3s`에 이미 올려 두었으므로 그 파일을 씁니다.

1. Docker Hub의 `komit-fe` 저장소 → **Tags**에서 최신 커밋 태그를 확인합니다.
2. 서버에서 실행합니다. service·escrow 이미지는 넘기지 않아 지금 돌고 있는 이미지가 유지됩니다.

   ```bash
   read -rsp "SERVICE_DB_PASSWORD: " SERVICE_DB_PASSWORD; echo
   read -rsp "ESCROW_DB_PASSWORD: " ESCROW_DB_PASSWORD; echo
   export SERVICE_DB_PASSWORD ESCROW_DB_PASSWORD
   export FE_IMAGE=docker.io/<계정>/komit-fe:<태그>
   # GitLab CI/CD 변수에 등록된 값과 같게 넣습니다.
   export BACKEND_API_BASE_URL=<GitLab 변수 값>
   export AI_API_BASE_URL=<GitLab 변수 값>

   sudo --preserve-env=SERVICE_DB_PASSWORD,ESCROW_DB_PASSWORD,FE_IMAGE,BACKEND_API_BASE_URL,AI_API_BASE_URL \
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
export SERVICE_DB_PASSWORD ESCROW_DB_PASSWORD

export SERVICE_IMAGE=docker.io/<계정>/komit-service:<태그>
export ESCROW_IMAGE=docker.io/<계정>/komit-escrow:<태그>
export FE_IMAGE=docker.io/<계정>/komit-fe:<태그>

# GitLab CI/CD 변수에 등록된 값과 같게 넣습니다.
export BACKEND_API_BASE_URL=<GitLab 변수 값>
export AI_API_BASE_URL=<GitLab 변수 값>

sudo --preserve-env=SERVICE_DB_PASSWORD,ESCROW_DB_PASSWORD,SERVICE_IMAGE,ESCROW_IMAGE,FE_IMAGE,BACKEND_API_BASE_URL,AI_API_BASE_URL \
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
| `curl`이 **502** | 30080에 받는 쪽이 없음 | `sudo kubectl -n komit get pods`로 service Pod가 Ready인지 확인 |
| `curl`이 **504** (약 60초 뒤) | nginx 컨테이너가 30080에 닿지 못함 | A-0의 `nodeport-addresses` 확인 |

## CI/CD

`develop`에 `service/`, `escrow/`, `fe/`, `deploy/k3s/` 변경이 병합되면 `k3s-deploy` 잡이 돕니다.

```text
service 변경 → service-docker-build-push ─┐ SERVICE_IMAGE (dotenv)
escrow 변경  → escrow-docker-build-push  ─┤ ESCROW_IMAGE  (dotenv)
fe 변경      → fe-docker-build-push      ─┤ FE_IMAGE      (dotenv)
                                          ▼
                                    k3s-deploy
                                    1. deploy/k3s를 서버 ~/komit-k3s로 복사
                                    2. 표준입력으로 비밀번호·이미지를 넘겨 deploy.sh 실행
```

| CI/CD 변수 | 설정 | 용도 |
|---|---|---|
| `SSH_ADDRESS`, `SSH_PRIVATE_KEY` | 목 배포와 공용 | 서버 접속 |
| `SERVICE_DB_PASSWORD`, `ESCROW_DB_PASSWORD` | Masked, Protected | DB 계정 Secret |
| `BACKEND_API_BASE_URL`, `AI_API_BASE_URL` | FE 이미지 빌드와 공용 | fe 실행 환경(`fe-config`). `ESCROW_API_BASE_URL`은 등록되어 있을 때만 넣습니다 |

- 이번 파이프라인에서 빌드되지 않은 모듈은 서버에서 돌고 있는 이미지를 유지합니다. `deploy/k3s/`만 바뀌면 이미지 교체 없이 설정만 반영합니다.
- 비밀번호는 SSH 명령줄 인자가 아니라 표준입력으로 전달해 서버 프로세스 목록에 남기지 않습니다.
- `resource_group`으로 배포가 동시에 두 번 돌지 않게 합니다.

## 수동 배포

다음 환경변수를 배포 서버나 CI의 보호된 변수로 주입합니다. 비밀번호를 저장소 파일에 기록하지 마세요.

```bash
export SERVICE_DB_PASSWORD=<password>
export ESCROW_DB_PASSWORD=<password>
export BACKEND_API_BASE_URL=<fe가 호출할 백엔드 주소>
export AI_API_BASE_URL=<fe가 호출할 AI 주소>

# 이번에 바뀐 모듈만 넘깁니다. 클러스터에 아직 없는 모듈은 반드시 넘깁니다.
export SERVICE_IMAGE=docker.io/<account>/komit-service:<commit-sha>
export ESCROW_IMAGE=docker.io/<account>/komit-escrow:<commit-sha>
export FE_IMAGE=docker.io/<account>/komit-fe:<commit-sha>

chmod +x deploy/k3s/deploy.sh
./deploy/k3s/deploy.sh
```

| 환경변수 | 필수 | 설명 |
|---|---|---|
| `SERVICE_DB_PASSWORD`, `ESCROW_DB_PASSWORD` | 예 | DB 소유자 계정 비밀번호. `service-db-app`, `escrow-db-app` Secret이 되어 CloudNativePG initdb와 Spring이 함께 읽습니다. |
| `BACKEND_API_BASE_URL`, `AI_API_BASE_URL` | 예 | fe 서버가 백엔드·AI를 호출할 주소. GitLab CI/CD 변수와 같은 값을 넣습니다. 값이 바뀌면 fe Pod가 새 값으로 다시 뜹니다. |
| `ESCROW_API_BASE_URL` | 아니요 | 값이 있을 때만 fe 실행 환경에 넣습니다. |
| `SERVICE_IMAGE`, `ESCROW_IMAGE`, `FE_IMAGE` | 아니요 | 비어 있으면 클러스터에서 돌고 있는 이미지를 유지합니다. 바뀌지 않은 모듈은 재시작하지 않습니다. 클러스터에 아직 없는 모듈은 필수입니다. |

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
- **단일 노드:** 운영 EC2가 한 대라 Primary와 Standby가 같은 노드와 디스크(local-path)에 있습니다. Pod 장애는 견디지만 서버나 디스크 장애에는 앱과 DB가 함께 멈추고, 백업이 없어 데이터도 복구할 수 없습니다.
- **Standby 전체 장애:** Standby가 모두 내려가 `-ro` Service에 대상이 없으면 `readOnly` 트랜잭션이 실패합니다. 쓰기 트랜잭션은 계속 동작합니다. Standby를 2대 이상 유지하는 이유입니다.
- **Flyway 실행 위치:** Flyway는 앱 Pod가 시작할 때마다 실행됩니다. PostgreSQL advisory lock으로 동시 실행은 막히지만, 백엔드 컨벤션 6.7(마이그레이션은 배포 파이프라인의 한 단계에서 실행)과는 아직 다릅니다.
