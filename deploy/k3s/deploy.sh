#!/usr/bin/env sh
set -eu

# SERVICE_IMAGE, ESCROW_IMAGE, FE_IMAGE, FE_TEST_IMAGE는 이번에 바뀐 모듈만 넘깁니다.
# 비어 있으면 클러스터에서 돌고 있는 이미지를 그대로 유지합니다. 모듈의 첫 배포에는 해당 이미지가 필요합니다.
SERVICE_IMAGE=${SERVICE_IMAGE:-}
ESCROW_IMAGE=${ESCROW_IMAGE:-}
FE_IMAGE=${FE_IMAGE:-}
# mock을 보는 테스트용 FE. 같은 소스를 /test 접두사로 구운 별도 이미지입니다.
FE_TEST_IMAGE=${FE_TEST_IMAGE:-}
# AI 서비스(Python). service Pod만 클러스터 안에서 부릅니다.
AI_IMAGE=${AI_IMAGE:-}
: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD is required}"
: "${ESCROW_DB_PASSWORD:?ESCROW_DB_PASSWORD is required}"
: "${SERVICE_REDIS_PASSWORD:?SERVICE_REDIS_PASSWORD is required}"
: "${SERVICE_MONGODB_ROOT_PASSWORD:?SERVICE_MONGODB_ROOT_PASSWORD is required}"
: "${SERVICE_MONGODB_PASSWORD:?SERVICE_MONGODB_PASSWORD is required}"
# 각 백엔드가 밖에서 보이는 주소입니다. nginx 경로 접두사까지 포함합니다. (예: https://komit.site/service)
: "${SERVICE_PUBLIC_BASE_URL:?SERVICE_PUBLIC_BASE_URL is required}"
: "${ESCROW_PUBLIC_BASE_URL:?ESCROW_PUBLIC_BASE_URL is required}"
# 루트 fe가 호출할 실제 서버 주소입니다. GitLab CI/CD 변수 값을 실행 환경에 넣습니다.
: "${BACKEND_API_BASE_URL:?BACKEND_API_BASE_URL is required}"
# service가 부를 AI 서비스 주소입니다. 클러스터 안 주소이며 밖으로 열지 않습니다.
# FE는 AI를 직접 부르지 않으므로 fe-config가 아니라 service-config로 들어갑니다.
: "${AI_API_BASE_URL:?AI_API_BASE_URL is required}"
# AI 서비스가 GMS를 부르는 키입니다. 없으면 AI Pod가 기동하지 않습니다.
: "${GMS_API_KEY:?GMS_API_KEY is required}"
# /test fe가 호출할 mock 주소입니다. 루트와 변수 자체를 분리해 뒤바뀌지 않게 합니다.
: "${TEST_BACKEND_API_BASE_URL:?TEST_BACKEND_API_BASE_URL is required}"
: "${ESCROW_API_BASE_URL:?ESCROW_API_BASE_URL is required}"
: "${ESCROW_CLIENT_API_KEY:?ESCROW_CLIENT_API_KEY is required}"
: "${ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE:?ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE is required}"
: "${ESCROW_ADMIN_USERNAME:?ESCROW_ADMIN_USERNAME is required}"
: "${ESCROW_ADMIN_PASSWORD_HASH:?ESCROW_ADMIN_PASSWORD_HASH is required}"
# 카카오 로그인. fe 의 인가 시작 Route Handler 가 실행 시점에 읽습니다. 없으면 로그인이 500 입니다.
: "${KAKAO_CLIENT_ID:?KAKAO_CLIENT_ID is required}"
: "${KAKAO_REDIRECT_URI:?KAKAO_REDIRECT_URI is required}"
# /test 전용 Redirect URI. 루트 주소와 섞이면 로그인 뒤 komit.site/로 빠집니다.
# 카카오 콘솔에도 이 주소를 등록해야 로그인이 됩니다.
: "${TEST_KAKAO_REDIRECT_URI:?TEST_KAKAO_REDIRECT_URI is required}"
# service 인증. application-prod.yml 이 환경변수로만 받고 기본값이 없어, 없으면 service Pod 가 기동하지 않습니다.
: "${KOMIT_USER_JWT_SECRET:?KOMIT_USER_JWT_SECRET is required}"
: "${KAKAO_TOKEN_ENCRYPTION_KEY:?KAKAO_TOKEN_ENCRYPTION_KEY is required}"
# 계좌번호·userKey 를 암호화해 보관하는 키입니다. 없으면 service Pod 가 기동 중 죽습니다.
: "${ACCOUNT_DATA_ENCRYPTION_KEY:?ACCOUNT_DATA_ENCRYPTION_KEY is required}"
: "${KAKAO_CLIENT_SECRET:?KAKAO_CLIENT_SECRET is required}"
# 파일 저장소 MinIO(base/minio.yaml). 내부 주소는 service Pod가 부르고(minio.komit.svc.cluster.local:9000),
# 공개 주소는 브라우저가 열 프리사인 URL의 호스트(https://file.komit.site)입니다. 자격은 MinIO 루트 계정으로도 쓰입니다.
: "${STORAGE_INTERNAL_ENDPOINT:?STORAGE_INTERNAL_ENDPOINT is required}"
: "${STORAGE_PUBLIC_ENDPOINT:?STORAGE_PUBLIC_ENDPOINT is required}"
: "${STORAGE_ACCESS_KEY:?STORAGE_ACCESS_KEY is required}"
: "${STORAGE_SECRET_KEY:?STORAGE_SECRET_KEY is required}"
STORAGE_BUCKET=${STORAGE_BUCKET:-komit}
# escrow 보안 설정. 운영에서는 application-local.yml의 개발 기본값을 사용하지 않습니다.
: "${CLIENT_API_KEY_HMAC_SECRET:?CLIENT_API_KEY_HMAC_SECRET is required}"
: "${SENSITIVE_DATA_HMAC_SECRET:?SENSITIVE_DATA_HMAC_SECRET is required}"
: "${FIELD_ENCRYPTION_KEY_BASE64:?FIELD_ENCRYPTION_KEY_BASE64 is required}"
: "${OPERATOR_CLIENT_API_KEY:?OPERATOR_CLIENT_API_KEY is required}"
: "${SSAFY_FINANCE_API_KEY:?SSAFY_FINANCE_API_KEY is required}"
: "${SSAFY_DEMAND_DEPOSIT_PRODUCT_NO:?SSAFY_DEMAND_DEPOSIT_PRODUCT_NO is required}"
: "${SSAFY_ESCROW_USER_KEY:?SSAFY_ESCROW_USER_KEY is required}"
: "${SSAFY_ESCROW_ACCOUNT_NO:?SSAFY_ESCROW_ACCOUNT_NO is required}"
: "${SSAFY_FEE_ACCOUNT_NO:?SSAFY_FEE_ACCOUNT_NO is required}"
: "${SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS:?SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS is required}"

case "$SSAFY_ESCROW_ACCOUNT_NO" in
  *[!0-9]*|'') echo "SSAFY_ESCROW_ACCOUNT_NO must contain exactly 16 digits" >&2; exit 1 ;;
esac
[ "${#SSAFY_ESCROW_ACCOUNT_NO}" -eq 16 ] || { echo "SSAFY_ESCROW_ACCOUNT_NO must contain exactly 16 digits" >&2; exit 1; }
case "$SSAFY_FEE_ACCOUNT_NO" in
  *[!0-9]*|'') echo "SSAFY_FEE_ACCOUNT_NO must contain exactly 16 digits" >&2; exit 1 ;;
esac
[ "${#SSAFY_FEE_ACCOUNT_NO}" -eq 16 ] || { echo "SSAFY_FEE_ACCOUNT_NO must contain exactly 16 digits" >&2; exit 1; }
[ "$SSAFY_FEE_ACCOUNT_NO" != "$SSAFY_ESCROW_ACCOUNT_NO" ] || { echo "SSAFY_FEE_ACCOUNT_NO must differ from SSAFY_ESCROW_ACCOUNT_NO" >&2; exit 1; }
case "$SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS" in
  *[!0-9]*|'') echo "SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS must be a positive integer" >&2; exit 1 ;;
esac
[ "$SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS" -gt 0 ] || { echo "SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS must be positive" >&2; exit 1; }
[ "$SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS" -le 30 ] || { echo "SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS must not exceed 30" >&2; exit 1; }

for NAME in CLIENT_API_KEY_HMAC_SECRET SENSITIVE_DATA_HMAC_SECRET OPERATOR_CLIENT_API_KEY; do
  eval "VALUE=\${$NAME}"
  if [ "${#VALUE}" -lt 32 ]; then
    echo "$NAME must contain at least 32 characters" >&2
    exit 1
  fi
done
# Mock은 명세에 정의된 고정 키를 사용합니다. 실제 Escrow 연동 키에만 충분한 엔트로피를 강제합니다.
case "$ESCROW_API_BASE_URL" in
  */mock/escrow|*/mock/escrow/) ;;
  *)
    if [ "${#ESCROW_CLIENT_API_KEY}" -lt 32 ]; then
      echo "ESCROW_CLIENT_API_KEY must contain at least 32 characters for real Escrow integration" >&2
      exit 1
    fi
    ;;
esac
if [ "${#ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE}" -gt 100 ]; then
  echo "ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE must not exceed 100 characters" >&2
  exit 1
fi
# S3 서명 규격상 엔드포인트에 경로를 둘 수 없습니다(minio-java: "no path allowed in endpoint").
# https://komit.site/file 같은 값은 service가 기동하지 않으므로 여기서 먼저 막습니다. 끝의 /만 허용합니다.
for NAME in STORAGE_INTERNAL_ENDPOINT STORAGE_PUBLIC_ENDPOINT; do
  eval "VALUE=\${$NAME}"
  VALUE=${VALUE%/}
  case "$VALUE" in
    http://*|https://*) ;;
    *) echo "$NAME must start with http:// or https://" >&2; exit 1 ;;
  esac
  case "${VALUE#*://}" in
    */*|'') echo "$NAME must be a host without a path (S3 signatures do not support sub-path endpoints)" >&2; exit 1 ;;
  esac
done
# MinIO 루트 계정 규칙. 짧으면 MinIO Pod가 기동하지 않습니다.
[ "${#STORAGE_ACCESS_KEY}" -ge 3 ] || { echo "STORAGE_ACCESS_KEY must contain at least 3 characters (MinIO root user)" >&2; exit 1; }
[ "${#STORAGE_SECRET_KEY}" -ge 8 ] || { echo "STORAGE_SECRET_KEY must contain at least 8 characters (MinIO root password)" >&2; exit 1; }
ACCOUNT_KEY_BYTES=$(printf '%s' "$ACCOUNT_DATA_ENCRYPTION_KEY" | base64 -d 2>/dev/null | wc -c | tr -d ' ')
if [ "$ACCOUNT_KEY_BYTES" != 32 ]; then
  echo "ACCOUNT_DATA_ENCRYPTION_KEY must encode exactly 32 bytes" >&2
  exit 1
fi
ENCRYPTION_KEY_BYTES=$(printf '%s' "$FIELD_ENCRYPTION_KEY_BASE64" | base64 -d 2>/dev/null | wc -c | tr -d ' ')
if [ "$ENCRYPTION_KEY_BYTES" != 32 ]; then
  echo "FIELD_ENCRYPTION_KEY_BASE64 must encode exactly 32 bytes" >&2
  exit 1
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

# 넘겨받은 이미지가 없으면 현재 Deployment의 이미지를 읽습니다.
resolve_image() {
  if [ -n "$1" ]; then
    printf '%s' "$1"
    return
  fi

  current=$(kubectl -n komit get deployment "$2" \
    -o jsonpath="{.spec.template.spec.containers[?(@.name==\"$2\")].image}" 2>/dev/null || true)
  if [ -z "$current" ]; then
    echo "$2 image is required because deployment/$2 does not exist yet" >&2
    return 1
  fi
  printf '%s' "$current"
}

# Kustomize literal 한 줄로 씁니다. YAML 작은따옴표 안에서는 '를 ''로 적습니다.
config_literal() {
  printf "      - '%s=%s'\n" "$1" "$(printf '%s' "$2" | sed "s/'/''/g")"
}

apply_basic_auth_secret() {
  kubectl -n komit create secret generic "$1" \
    --type=kubernetes.io/basic-auth \
    --from-literal=username="$2" \
    --from-literal=password="$3" \
    --dry-run=client -o yaml | kubectl apply -f -
}

apply_password_secret() {
  kubectl -n komit create secret generic "$1" \
    --from-literal=password="$2" \
    --dry-run=client -o yaml | kubectl apply -f -
}

apply_mongodb_secret() {
  kubectl -n komit create secret generic service-mongodb-auth \
    --from-literal=root-username=root \
    --from-literal=root-password="$1" \
    --from-literal=username=service \
    --from-literal=password="$2" \
    --dry-run=client -o yaml | kubectl apply -f -
}

# 오퍼레이터가 만드는 StatefulSet은 CR을 적용한 직후에는 아직 없을 수 있어 생길 때까지 기다린 뒤 rollout을 봅니다.
wait_statefulset() {
  until kubectl -n komit get statefulset "$1" >/dev/null 2>&1; do sleep 3; done
  kubectl -n komit rollout status statefulset/"$1" --timeout=300s
}

kubectl apply -f "$SCRIPT_DIR/base/namespace.yaml"

SERVICE_IMAGE=$(resolve_image "$SERVICE_IMAGE" service)
ESCROW_IMAGE=$(resolve_image "$ESCROW_IMAGE" escrow)
FE_IMAGE=$(resolve_image "$FE_IMAGE" fe)
FE_TEST_IMAGE=$(resolve_image "$FE_TEST_IMAGE" fe-test)
AI_IMAGE=$(resolve_image "$AI_IMAGE" ai)
echo "service image: $SERVICE_IMAGE"
echo "escrow image:  $ESCROW_IMAGE"
echo "fe image:      $FE_IMAGE"
echo "fe-test image: $FE_TEST_IMAGE"
echo "ai image:      $AI_IMAGE"

# 1. CloudNativePG 오퍼레이터
kubectl apply -f "$SCRIPT_DIR/platform/cloudnative-pg.yaml"
until kubectl get crd clusters.postgresql.cnpg.io >/dev/null 2>&1; do sleep 3; done
kubectl wait --for=condition=Established crd/clusters.postgresql.cnpg.io --timeout=300s
until [ -n "$(kubectl -n cnpg-system get deployment -l app.kubernetes.io/name=cloudnative-pg -o name 2>/dev/null)" ]; do sleep 3; done
kubectl -n cnpg-system wait --for=condition=Available deployment -l app.kubernetes.io/name=cloudnative-pg --timeout=300s

# 1-1. Redis 오퍼레이터
kubectl apply -f "$SCRIPT_DIR/platform/redis-operator.yaml"
until kubectl get crd redisreplications.redis.redis.opstreelabs.in redissentinels.redis.redis.opstreelabs.in >/dev/null 2>&1; do sleep 3; done
kubectl wait --for=condition=Established crd/redisreplications.redis.redis.opstreelabs.in crd/redissentinels.redis.redis.opstreelabs.in --timeout=300s
until kubectl -n ot-operators get deployment redis-operator >/dev/null 2>&1; do sleep 3; done
kubectl -n ot-operators wait --for=condition=Available deployment/redis-operator --timeout=300s

# 2. DB·Redis 계정 Secret, service-db, escrow-db, service-redis
apply_basic_auth_secret service-db-app service "$SERVICE_DB_PASSWORD"
apply_basic_auth_secret escrow-db-app escrow "$ESCROW_DB_PASSWORD"
apply_password_secret service-redis-auth "$SERVICE_REDIS_PASSWORD"
apply_mongodb_secret "$SERVICE_MONGODB_ROOT_PASSWORD" "$SERVICE_MONGODB_PASSWORD"
# service 인증 Secret 은 아래 Kustomize secretGenerator 가 만듭니다. 이름에 해시가 붙어 값이 바뀌면 Pod 가 새 값으로 다시 뜹니다.
kubectl apply -k "$SCRIPT_DIR/database"
kubectl -n komit wait --for=condition=Ready cluster/service-db cluster/escrow-db --timeout=600s
# Redis 노드가 모두 뜬 뒤 Sentinel이 Primary를 찾아 기동합니다.
wait_statefulset service-redis
wait_statefulset service-redis-sentinel
wait_statefulset service-mongodb
sh "$SCRIPT_DIR/verify-mongodb.sh"

# 3. 애플리케이션: 배포할 이미지를 Kustomize overlay에 고정해 한 번의 apply로 반영합니다.
# Kustomize는 절대 경로 base를 받지 않으므로 임시 디렉터리에 base와 prod를 복사해 overlay를 만듭니다.
cp -R "$SCRIPT_DIR/base" "$WORK_DIR/base"
cp -R "$SCRIPT_DIR/prod" "$WORK_DIR/prod"
mkdir "$WORK_DIR/apps"
cat > "$WORK_DIR/apps/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../prod
images:
  - name: komit-service
    newName: ${SERVICE_IMAGE%:*}
    newTag: "${SERVICE_IMAGE##*:}"
  - name: komit-escrow
    newName: ${ESCROW_IMAGE%:*}
    newTag: "${ESCROW_IMAGE##*:}"
  - name: komit-fe
    newName: ${FE_IMAGE%:*}
    newTag: "${FE_IMAGE##*:}"
  - name: komit-fe-test
    newName: ${FE_TEST_IMAGE%:*}
    newTag: "${FE_TEST_IMAGE##*:}"
  - name: komit-ai
    newName: ${AI_IMAGE%:*}
    newTag: "${AI_IMAGE##*:}"
# 값이 바뀌면 ConfigMap 이름의 해시가 바뀌어 fe Pod가 새 값으로 다시 뜹니다.
configMapGenerator:
  - name: fe-config
    namespace: komit
    literals:
EOF
{
  config_literal BACKEND_API_BASE_URL "$BACKEND_API_BASE_URL"
  config_literal KAKAO_CLIENT_ID "$KAKAO_CLIENT_ID"
  config_literal KAKAO_REDIRECT_URI "$KAKAO_REDIRECT_URI"
  # `/test`는 mock 전용 변수만 받습니다. 루트 FE 변수와 공유하지 않습니다.
  printf '  - name: fe-test-config\n    namespace: komit\n    literals:\n'
  config_literal BACKEND_API_BASE_URL "$TEST_BACKEND_API_BASE_URL"
  config_literal KAKAO_CLIENT_ID "$KAKAO_CLIENT_ID"
  config_literal KAKAO_REDIRECT_URI "$TEST_KAKAO_REDIRECT_URI"
  # 백엔드가 자기 공개 주소를 알도록 넣습니다. 값이 바뀌면 해당 Pod만 새 값으로 다시 뜹니다.
  printf '  - name: service-config\n    namespace: komit\n    literals:\n'
  config_literal APP_PUBLIC_BASE_URL "$SERVICE_PUBLIC_BASE_URL"
  config_literal ESCROW_API_BASE_URL "$ESCROW_API_BASE_URL"
  # AI 서비스를 부르는 쪽은 service입니다. FE는 이 주소를 알지 못합니다.
  config_literal AI_API_BASE_URL "$AI_API_BASE_URL"
  config_literal STORAGE_INTERNAL_ENDPOINT "$STORAGE_INTERNAL_ENDPOINT"
  config_literal STORAGE_PUBLIC_ENDPOINT "$STORAGE_PUBLIC_ENDPOINT"
  config_literal STORAGE_BUCKET "$STORAGE_BUCKET"
  printf '  - name: escrow-config\n    namespace: komit\n    literals:\n'
  config_literal APP_PUBLIC_BASE_URL "$ESCROW_PUBLIC_BASE_URL"
  config_literal SSAFY_DEMAND_DEPOSIT_PRODUCT_NO "$SSAFY_DEMAND_DEPOSIT_PRODUCT_NO"
  config_literal SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS "$SSAFY_VIRTUAL_ACCOUNT_VALIDITY_DAYS"
  config_literal ESCROW_ADMIN_USERNAME "$ESCROW_ADMIN_USERNAME"
  # Secret 값이 바뀌면 이름 해시와 Pod template이 함께 바뀌어 Escrow가 자동 재시작됩니다.
  printf 'secretGenerator:\n  - name: escrow-security\n    namespace: komit\n    literals:\n'
  config_literal CLIENT_API_KEY_HMAC_SECRET "$CLIENT_API_KEY_HMAC_SECRET"
  config_literal SENSITIVE_DATA_HMAC_SECRET "$SENSITIVE_DATA_HMAC_SECRET"
  config_literal FIELD_ENCRYPTION_KEY_BASE64 "$FIELD_ENCRYPTION_KEY_BASE64"
  config_literal OPERATOR_CLIENT_API_KEY "$OPERATOR_CLIENT_API_KEY"
  config_literal ESCROW_BOOTSTRAP_CLIENT_API_KEY "$ESCROW_CLIENT_API_KEY"
  config_literal ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE "$ESCROW_CLIENT_ONE_WON_AUTH_MESSAGE"
  config_literal ESCROW_ADMIN_PASSWORD_HASH "$ESCROW_ADMIN_PASSWORD_HASH"
  config_literal SSAFY_FINANCE_API_KEY "$SSAFY_FINANCE_API_KEY"
  config_literal SSAFY_ESCROW_USER_KEY "$SSAFY_ESCROW_USER_KEY"
  config_literal SSAFY_ESCROW_ACCOUNT_NO "$SSAFY_ESCROW_ACCOUNT_NO"
  config_literal SSAFY_FEE_ACCOUNT_NO "$SSAFY_FEE_ACCOUNT_NO"
  # service 인증·암호화 키. 값이 바뀌면 이름 해시와 Pod template 이 함께 바뀌어 service 가 자동 재시작됩니다.
  printf '  - name: service-auth\n    namespace: komit\n    literals:\n'
  config_literal KOMIT_USER_JWT_SECRET "$KOMIT_USER_JWT_SECRET"
  config_literal KAKAO_TOKEN_ENCRYPTION_KEY "$KAKAO_TOKEN_ENCRYPTION_KEY"
  config_literal ACCOUNT_DATA_ENCRYPTION_KEY "$ACCOUNT_DATA_ENCRYPTION_KEY"
  config_literal KAKAO_CLIENT_ID "$KAKAO_CLIENT_ID"
  config_literal KAKAO_CLIENT_SECRET "$KAKAO_CLIENT_SECRET"
  config_literal ESCROW_CLIENT_API_KEY "$ESCROW_CLIENT_API_KEY"
  # 파일 저장소 자격. 서비스 인증 키와 수명주기를 분리합니다.
  printf '  - name: service-storage\n    namespace: komit\n    literals:\n'
  config_literal STORAGE_ACCESS_KEY "$STORAGE_ACCESS_KEY"
  config_literal STORAGE_SECRET_KEY "$STORAGE_SECRET_KEY"
  # AI 서비스가 GMS를 부르는 키. 값이 바뀌면 이름 해시가 바뀌어 AI Pod가 다시 뜹니다.
  printf '  - name: ai-secret
    namespace: komit
    literals:
'
  config_literal GMS_API_KEY "$GMS_API_KEY"
} >> "$WORK_DIR/apps/kustomization.yaml"
# 이전 역방향 구성의 Service가 같은 NodePort(30083)를 점유하므로 첫 전환 때만 제거합니다.
# Deployment는 새 fe-test가 정상 기동한 뒤 제거해 전환 중 진단 로그를 남길 수 있게 합니다.
kubectl -n komit delete service fe-be --ignore-not-found
# minio-init Job은 service-config 이름 해시가 바뀌면 template이 달라져 다시 apply할 수 없으므로 먼저 지웁니다.
# 버킷 생성은 --ignore-existing이라 매 배포마다 다시 돌아도 안전합니다.
kubectl -n komit delete job minio-init --ignore-not-found
kubectl apply -k "$WORK_DIR/apps"
rollout() {
  NAME=$1
  if kubectl -n komit rollout status "deployment/$NAME" --timeout=300s; then return 0; fi
  echo "deployment/$NAME rollout failed; collecting diagnostics" >&2
  kubectl -n komit get deployment,replicaset,pod -l "app.kubernetes.io/name=$NAME" -o wide >&2 || true
  kubectl -n komit describe deployment "$NAME" >&2 || true
  for POD in $(kubectl -n komit get pod -l "app.kubernetes.io/name=$NAME" -o name 2>/dev/null); do
    kubectl -n komit describe "$POD" >&2 || true
    kubectl -n komit logs "$POD" --all-containers --tail=200 >&2 || true
    kubectl -n komit logs "$POD" --all-containers --previous --tail=200 >&2 || true
  done
  return 1
}
# 파일 저장소가 먼저 준비되어야 service의 업로드·읽기 주소 발급이 바로 동작합니다.
rollout minio
kubectl -n komit wait --for=condition=Complete job/minio-init --timeout=300s
# service가 AI를 부르므로 AI가 먼저 준비되어야 첫 요청이 떨어지지 않습니다.
rollout ai
rollout service
rollout escrow
rollout fe
rollout fe-test
kubectl -n komit delete deployment fe-be --ignore-not-found
kubectl -n komit get pods -o wide
