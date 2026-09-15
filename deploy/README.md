# deploy

배포 서버(EC2) 설정. **이미지는 CI가 굽고 Docker Hub에 올린 것을 서버가 받아 쓴다.**
서버에는 소스를 두지 않는다.

K3s에서 `service`와 `escrow`를 여러 Pod로 운영하는 구성은 [`k3s/README.md`](k3s/README.md)를 참고한다. 기존 `docker-compose.yml`은 목 서버용 단일 EC2 배포 참고본으로 유지한다.

```
개발자 → GitLab(develop) → CI: docker build/push → Docker Hub
                                                      ↓
                                    EC2: docker compose pull && up -d
```

## 구성

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | 서버에 두는 compose의 참고본. 실제로 쓰이는 건 서버에 있는 파일이다 |
| `nginx/conf.d/default.conf` | 리버스 프록시 — `/api/`, `/docs`, `/health`만 목 서버로 넘긴다 |

이미지 정의는 서비스 디렉터리에 둔다. 목 서버는 [`mock/Dockerfile`](../mock/Dockerfile).

## 로컬에서 확인

```bash
docker build -t komit-mock:local mock
docker run --rm -p 8080:8080 komit-mock:local
curl localhost:8080/health
```

compose까지 묶어서:

```bash
MOCK_IMAGE=komit-mock:local docker compose -f deploy/docker-compose.yml up -d
```

## 서버 준비 (최초 1회)

1. Docker Engine + Compose 설치
2. 배포용 디렉터리를 만들고 `docker-compose.yml`과 `nginx/`를 올린다
3. Docker Hub가 private 저장소면 `docker login`을 한 번 해 둔다
4. 보안그룹에서 80(및 443) 개방

서버의 compose 파일은 손으로 관리한다. 여기 있는 파일을 고쳤다면 서버 쪽도 맞춘다.

## 배포

CI가 SSH로 아래를 실행한다.

```bash
MOCK_IMAGE=<계정>/komit-mock:<tag> docker compose pull && docker compose up -d
```

## 아직 없는 것

- HTTPS — 인증서 발급 방식(Let's Encrypt / SSAFY 제공)이 정해지면 nginx에 443을 추가한다
- BE·FE 서비스 — 코드가 생기면 compose에 서비스를 추가하고 nginx 경로를 나눈다
