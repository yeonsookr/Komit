#!/usr/bin/env sh
set -eu

# 모든 K3s 노드에 짧은 mongosh Job을 직접 배치해 service 전용 계정으로 DNS·TCP·인증을 함께 확인합니다.
index=0
for node in $(kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'); do
  index=$((index + 1))
  job="verify-service-mongodb-$index"
  kubectl -n komit delete job "$job" --ignore-not-found >/dev/null
  cat <<EOF | kubectl apply -f - >/dev/null
apiVersion: batch/v1
kind: Job
metadata:
  name: $job
  namespace: komit
  labels:
    app.kubernetes.io/name: service-mongodb-verifier
    app.kubernetes.io/part-of: komit
spec:
  backoffLimit: 2
  ttlSecondsAfterFinished: 300
  template:
    metadata:
      labels:
        app.kubernetes.io/name: service-mongodb-verifier
        app.kubernetes.io/part-of: komit
    spec:
      nodeName: $node
      restartPolicy: Never
      containers:
        - name: mongosh
          image: mongo:8.0.30-noble
          command: ["/bin/sh", "-ec"]
          args:
            - >-
              mongosh --quiet --host service-mongodb.komit.svc.cluster.local:27017
              --username "\$MONGODB_USERNAME" --password "\$MONGODB_PASSWORD"
              --authenticationDatabase service service
              --eval 'quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)'
          env:
            - name: MONGODB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: service-mongodb-auth
                  key: username
            - name: MONGODB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: service-mongodb-auth
                  key: password
          securityContext:
            allowPrivilegeEscalation: false
            capabilities:
              drop: ["ALL"]
            runAsNonRoot: true
            runAsUser: 999
            runAsGroup: 999
            seccompProfile:
              type: RuntimeDefault
EOF
  if ! kubectl -n komit wait --for=condition=complete "job/$job" --timeout=180s; then
    kubectl -n komit describe "job/$job" >&2 || true
    kubectl -n komit logs "job/$job" >&2 || true
    exit 1
  fi
  echo "MongoDB connection verified from node: $node"
  kubectl -n komit delete job "$job" --ignore-not-found >/dev/null
done

[ "$index" -gt 0 ] || { echo "No Kubernetes nodes found" >&2; exit 1; }
