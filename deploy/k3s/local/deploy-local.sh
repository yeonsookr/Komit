#!/bin/sh
# docker-compose의 deployer 컨테이너에서 실행합니다.
set -eu

K3S_DIR=/workspace/deploy/k3s

wait_for() {
  description=$1
  shift
  attempts=0
  until "$@" >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 100 ]; then
      echo "timed out waiting for $description" >&2
      exit 1
    fi
    sleep 3
  done
}

kubectl apply -f "$K3S_DIR/base/namespace.yaml"

# 1. CloudNativePG 오퍼레이터
kubectl apply -f "$K3S_DIR/platform/cloudnative-pg.yaml"
wait_for "CloudNativePG CRD" kubectl get crd clusters.postgresql.cnpg.io
kubectl wait --for=condition=Established crd/clusters.postgresql.cnpg.io --timeout=300s
wait_for "CloudNativePG operator deployment" kubectl -n cnpg-system get deployment -l app.kubernetes.io/name=cloudnative-pg -o name
kubectl -n cnpg-system wait --for=condition=Available deployment -l app.kubernetes.io/name=cloudnative-pg --timeout=300s

# 2. service-db, escrow-db
kubectl apply -k "$K3S_DIR/local/database"
kubectl -n komit wait --for=condition=Ready cluster/service-db cluster/escrow-db --timeout=600s

# 3. 애플리케이션
kubectl apply -k "$K3S_DIR/local/apps"
kubectl -n komit rollout status deployment/service --timeout=300s
kubectl -n komit rollout status deployment/escrow --timeout=300s
kubectl -n komit get pods -o wide
