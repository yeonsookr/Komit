# syntax=docker/dockerfile:1

FROM node:24-alpine AS deps

WORKDIR /app

# 락파일 기준으로만 설치합니다. CI와 이미지가 같은 의존성 트리를 쓰게 하려는 것입니다.
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS builder

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# 빌드 시점에 서버 전용 환경 변수를 요구하지 않도록 텔레메트리만 끕니다.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup --system --gid 10001 komit \
    && adduser --system --uid 10001 --ingroup komit komit

# standalone 출력은 서버 구동에 필요한 것만 담고 있습니다. static/public은 따로 붙여야 합니다.
COPY --from=builder --chown=komit:komit /app/.next/standalone ./
COPY --from=builder --chown=komit:komit /app/.next/static ./.next/static
COPY --from=builder --chown=komit:komit /app/public ./public

USER komit

EXPOSE 3000

ENTRYPOINT ["node", "server.js"]
