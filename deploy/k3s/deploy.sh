#!/usr/bin/env sh
set -eu

# SERVICE_IMAGE, ESCROW_IMAGE, FE_IMAGE는 이번에 바뀐 모듈만 넘깁니다.
# 비어 있으면 클러스터에서 돌고 있는 이미지를 그대로 유지합니다. 모듈의 첫 배포에는 해당 이미지가 필요합니다.
SERVICE_IMAGE=${SERVICE_IMAGE:-}
ESCROW_IMAGE=${ESCROW_IMAGE:-}
FE_IMAGE=${FE_IMAGE:-}
: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD is required}"
: "${ESCROW_DB_PASSWORD:?ESCROW_DB_PASSWORD is required}"
# fe 서버가 백엔드를 호출할 주소입니다. 이미지 빌드와 같은 GitLab CI/CD 변수 값을 실행 환경에도 넣습니다.
: "${BACKEND_API_BASE_URL:?BACKEND_API_BASE_URL is required}"
: "${AI_API_BASE_URL:?AI_API_BASE_URL is required}"
ESCROW_API_BASE_URL=${ESCROW_API_BASE_URL:-}
# 카카오 로그인. fe 의 인가 시작 Route Handler 가 실행 시점에 읽습니다. 없으면 로그인이 500 입니다.
: "${KAKAO_CLIENT_ID:?KAKAO_CLIENT_ID is required}"
: "${KAKAO_REDIRECT_URI:?KAKAO_REDIRECT_URI is required}"

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

kubectl apply -f "$SCRIPT_DIR/base/namespace.yaml"

SERVICE_IMAGE=$(resolve_image "$SERVICE_IMAGE" service)
ESCROW_IMAGE=$(resolve_image "$ESCROW_IMAGE" escrow)
FE_IMAGE=$(resolve_image "$FE_IMAGE" fe)
echo "service image: $SERVICE_IMAGE"
echo "escrow image:  $ESCROW_IMAGE"
echo "fe image:      $FE_IMAGE"

# 1. CloudNativePG 오퍼레이터
kubectl apply -f "$SCRIPT_DIR/platform/cloudnative-pg.yaml"
until kubectl get crd clusters.postgresql.cnpg.io >/dev/null 2>&1; do sleep 3; done
kubectl wait --for=condition=Established crd/clusters.postgresql.cnpg.io --timeout=300s
until [ -n "$(kubectl -n cnpg-system get deployment -l app.kubernetes.io/name=cloudnative-pg -o name 2>/dev/null)" ]; do sleep 3; done
kubectl -n cnpg-system wait --for=condition=Available deployment -l app.kubernetes.io/name=cloudnative-pg --timeout=300s

# 2. DB 계정 Secret, service-db, escrow-db
apply_basic_auth_secret service-db-app service "$SERVICE_DB_PASSWORD"
apply_basic_auth_secret escrow-db-app escrow "$ESCROW_DB_PASSWORD"
kubectl apply -k "$SCRIPT_DIR/database"
kubectl -n komit wait --for=condition=Ready cluster/service-db cluster/escrow-db --timeout=600s

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
    newTag: ${SERVICE_IMAGE##*:}
  - name: komit-escrow
    newName: ${ESCROW_IMAGE%:*}
    newTag: ${ESCROW_IMAGE##*:}
  - name: komit-fe
    newName: ${FE_IMAGE%:*}
    newTag: ${FE_IMAGE##*:}
# 값이 바뀌면 ConfigMap 이름의 해시가 바뀌어 fe Pod가 새 값으로 다시 뜹니다.
configMapGenerator:
  - name: fe-config
    namespace: komit
    literals:
EOF
{
  config_literal BACKEND_API_BASE_URL "$BACKEND_API_BASE_URL"
  config_literal AI_API_BASE_URL "$AI_API_BASE_URL"
  if [ -n "$ESCROW_API_BASE_URL" ]; then
    config_literal ESCROW_API_BASE_URL "$ESCROW_API_BASE_URL"
  fi
  config_literal KAKAO_CLIENT_ID "$KAKAO_CLIENT_ID"
  config_literal KAKAO_REDIRECT_URI "$KAKAO_REDIRECT_URI"
} >> "$WORK_DIR/apps/kustomization.yaml"
kubectl apply -k "$WORK_DIR/apps"
kubectl -n komit rollout status deployment/service --timeout=300s
kubectl -n komit rollout status deployment/escrow --timeout=300s
kubectl -n komit rollout status deployment/fe --timeout=300s
kubectl -n komit get pods -o wide
