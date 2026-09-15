# syntax=docker/dockerfile:1

# 공식 pgpool/pgpool 이미지는 4.4.3(2023)에서 갱신이 멈춰 CVE-2025-46801 영향 버전입니다.
# 배포판 패키지로 수정 버전을 고정해 직접 굽습니다.
FROM alpine:3.24

RUN apk add --no-cache pgpool=4.6.7-r0 \
    && addgroup -S -g 10001 pgpool \
    && adduser -S -D -H -u 10001 -G pgpool pgpool

COPY --chmod=0755 entrypoint.sh /usr/local/bin/pgpool-entrypoint

USER 10001

EXPOSE 5432

ENTRYPOINT ["/usr/local/bin/pgpool-entrypoint"]
