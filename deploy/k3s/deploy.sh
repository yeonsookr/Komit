#!/usr/bin/env sh
set -eu

: "${SERVICE_IMAGE:?SERVICE_IMAGE is required}"
: "${ESCROW_IMAGE:?ESCROW_IMAGE is required}"
: "${PGPOOL_IMAGE:?PGPOOL_IMAGE is required}"
: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD is required}"
: "${SERVICE_DB_MONITOR_PASSWORD:?SERVICE_DB_MONITOR_PASSWORD is required}"
: "${ESCROW_DB_PASSWORD:?ESCROW_DB_PASSWORD is required}"
: "${ESCROW_DB_MONITOR_PASSWORD:?ESCROW_DB_MONITOR_PASSWORD is required}"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GENERATED_DIR="$SCRIPT_DIR/.generated"

apply_basic_auth_secret() {
  kubectl -n komit create secret generic "$1" \
    --type=kubernetes.io/basic-auth \
    --from-literal=username="$2" \
    --from-literal=password="$3" \
    --dry-run=client -o yaml | kubectl apply -f -
}

# 배포할 이미지를 Kustomize overlay에 고정해 한 번의 apply로 반영합니다.
write_overlay() {
  mkdir -p "$GENERATED_DIR/$1"
  cat > "$GENERATED_DIR/$1/kustomization.yaml"
}

kubectl apply -f "$SCRIPT_DIR/base/namespace.yaml"

# 1. CloudNativePG 오퍼레이터
kubectl apply -f "$SCRIPT_DIR/platform/cloudnative-pg.yaml"
until kubectl get crd clusters.postgresql.cnpg.io >/dev/null 2>&1; do sleep 3; done
kubectl wait --for=condition=Established crd/clusters.postgresql.cnpg.io --timeout=300s
until [ -n "$(kubectl -n cnpg-system get deployment -l app.kubernetes.io/name=cloudnative-pg -o name 2>/dev/null)" ]; do sleep 3; done
kubectl -n cnpg-system wait --for=condition=Available deployment -l app.kubernetes.io/name=cloudnative-pg --timeout=300s

# 2. DB 계정 Secret, service-db, escrow-db, Pgpool
apply_basic_auth_secret service-db-app service "$SERVICE_DB_PASSWORD"
apply_basic_auth_secret service-db-pgpool-monitor pgpool_monitor "$SERVICE_DB_MONITOR_PASSWORD"
apply_basic_auth_secret escrow-db-app escrow "$ESCROW_DB_PASSWORD"
apply_basic_auth_secret escrow-db-pgpool-monitor pgpool_monitor "$ESCROW_DB_MONITOR_PASSWORD"

write_overlay database <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../database
images:
  - name: komit-pgpool
    newName: ${PGPOOL_IMAGE%:*}
    newTag: ${PGPOOL_IMAGE##*:}
EOF
kubectl apply -k "$GENERATED_DIR/database"
kubectl -n komit wait --for=condition=Ready cluster/service-db cluster/escrow-db --timeout=600s
kubectl -n komit rollout status deployment/service-db-pool --timeout=180s
kubectl -n komit rollout status deployment/escrow-db-pool --timeout=180s

# 3. 애플리케이션
write_overlay apps <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
images:
  - name: komit-service
    newName: ${SERVICE_IMAGE%:*}
    newTag: ${SERVICE_IMAGE##*:}
  - name: komit-escrow
    newName: ${ESCROW_IMAGE%:*}
    newTag: ${ESCROW_IMAGE##*:}
EOF
kubectl apply -k "$GENERATED_DIR/apps"
kubectl -n komit rollout status deployment/service --timeout=300s
kubectl -n komit rollout status deployment/escrow --timeout=300s
kubectl -n komit get pods -o wide
