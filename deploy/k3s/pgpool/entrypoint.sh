#!/bin/sh
set -eu

: "${PGPOOL_PRIMARY_HOST:?PGPOOL_PRIMARY_HOST is required}"
: "${PGPOOL_REPLICA_HOST:?PGPOOL_REPLICA_HOST is required}"
: "${APP_DB_USERNAME:?APP_DB_USERNAME is required}"
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}"
: "${MONITOR_DB_USERNAME:?MONITOR_DB_USERNAME is required}"
: "${MONITOR_DB_PASSWORD:?MONITOR_DB_PASSWORD is required}"

CONFIG_SOURCE=/etc/pgpool-config
CONFIG_DIR=/tmp/pgpool

# 루트 파일시스템이 읽기 전용이므로 설정은 /tmp에서 조립합니다.
mkdir -p "$CONFIG_DIR"
cp "$CONFIG_SOURCE/pgpool.conf" "$CONFIG_SOURCE/pool_hba.conf" "$CONFIG_DIR/"

# 백엔드 0은 CloudNativePG의 -rw(항상 현재 Primary), 1은 -ro(Standby들)입니다.
# 장애 감지와 승격은 CloudNativePG가 맡으므로 Pgpool의 failover는 막아 둡니다.
cat >> "$CONFIG_DIR/pgpool.conf" <<EOF

backend_hostname0 = '$PGPOOL_PRIMARY_HOST'
backend_port0 = 5432
backend_weight0 = 0
backend_flag0 = 'ALWAYS_PRIMARY|DISALLOW_TO_FAILOVER'

backend_hostname1 = '$PGPOOL_REPLICA_HOST'
backend_port1 = 5432
backend_weight1 = 1
backend_flag1 = 'DISALLOW_TO_FAILOVER'
EOF

umask 077
printf '%s:TEXT%s\n%s:TEXT%s\n' \
  "$APP_DB_USERNAME" "$APP_DB_PASSWORD" \
  "$MONITOR_DB_USERNAME" "$MONITOR_DB_PASSWORD" > "$CONFIG_DIR/pool_passwd"
: > "$CONFIG_DIR/pcp.conf"

exec pgpool -n \
  -f "$CONFIG_DIR/pgpool.conf" \
  -a "$CONFIG_DIR/pool_hba.conf" \
  -F "$CONFIG_DIR/pcp.conf"
