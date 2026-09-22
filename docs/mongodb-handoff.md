# Service MongoDB 설정 인수인계

## 범위

이번 변경은 `service`가 MongoDB에 연결할 수 있는 기반만 제공합니다. MongoDB 도메인 문서, Repository, 인덱스와 데이터 보관 정책은 구현하지 않았습니다. 어떤 데이터를 MongoDB에 저장할지는 `RULES.md`의 미확정 항목을 먼저 확정해야 합니다.

## 연결 경로

```text
GitLab CI/CD Variables
  ├─ SERVICE_MONGODB_ROOT_PASSWORD
  └─ SERVICE_MONGODB_PASSWORD
          ↓ .gitlab-ci.yml (필수값 검사, Base64로 SSH 표준입력 전달)
      deploy/k3s/deploy.sh
          ↓ service-mongodb-auth Secret
      service-mongodb StatefulSet ── service-mongodb:27017
          ↑                                  ↑
   최초 root/app 계정 생성          service Pod의 MONGODB_* 환경변수
```

Spring Boot 4 설정은 `spring.mongodb.*`를 사용합니다. 연결에 필요한 환경변수는 다음과 같습니다.

| 환경변수 | 운영 값 | 비고 |
|---|---|---|
| `MONGODB_HOST` | `service-mongodb` | K3s 내부 Service 이름 |
| `MONGODB_PORT` | `27017` | 기본값 있음 |
| `MONGODB_DATABASE` | `service` | 서비스 전용 DB |
| `MONGODB_USERNAME` | `service` | `service` DB의 `readWrite` 계정 |
| `MONGODB_PASSWORD` | Secret 참조 | 저장소에 원문 금지 |

## 환경별 구성

- Local Compose: `deploy/local/docker-compose.yml`이 MongoDB와 초기화 스크립트를 함께 올립니다. 기본값은 로컬 개발 전용이며 `deploy/local/.env`에서 덮어쓸 수 있습니다.
- 로컬 K3s: `deploy/k3s/local/database/secrets.yaml`의 개발 전용 자격을 씁니다.
- 운영 K3s: GitLab의 Masked, Protected 변수 두 개를 `deploy.sh`가 `service-mongodb-auth` Secret으로 만듭니다.

## 검증

Local Compose:

```bash
docker compose -f deploy/local/docker-compose.yml up -d --build mongodb service
docker compose -f deploy/local/docker-compose.yml exec -T mongodb \
  mongosh --quiet --username service --password service \
  --authenticationDatabase service service \
  --eval 'quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)'
curl -fsS http://localhost:8080/actuator/health
```

K3s:

```bash
kubectl -n komit rollout status statefulset/service-mongodb --timeout=600s
sh deploy/k3s/verify-mongodb.sh
kubectl -n komit get pods -o wide -l app.kubernetes.io/name=service
kubectl -n komit get --raw /api/v1/namespaces/komit/services/http:service:8080/proxy/actuator/health
```

`verify-mongodb.sh`는 클러스터의 각 노드에 인증 연결 Job을 직접 배치합니다. 노드가 추가되어도 같은 명령으로 모든 노드를 다시 검사할 수 있습니다.

## 개발 담당자가 이어서 할 일

1. 저장할 도메인과 보관·삭제 정책을 확정하고 `RULES.md`에 반영합니다.
2. Spring Data MongoDB 문서 모델과 Repository를 도메인 패키지 안에 추가합니다.
3. 조회 패턴에 맞는 인덱스를 코드로 명시하고 통합 테스트를 추가합니다.
4. 운영에 고가용성이 필요하면 단일 StatefulSet을 replica set 또는 관리형 MongoDB로 전환합니다.

## 주의사항

- 공식 MongoDB 초기화 스크립트는 데이터 디렉터리가 비어 있을 때만 실행됩니다.
- 기존 PVC가 있는 환경에서 GitLab 변수나 Secret만 바꾸면 실제 MongoDB 계정 비밀번호는 회전하지 않습니다.
- `SERVICE_MONGODB_ROOT_PASSWORD`는 service Pod에 전달되지 않습니다.
- MongoDB를 외부 NodePort로 노출하지 않습니다. 접속은 클러스터 내부에서만 허용합니다.
