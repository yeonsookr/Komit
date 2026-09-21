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

# next build가 페이지 정보를 모으며 서버 API 모듈을 불러올 때 이 값들을 요구합니다.
# ARG라서 builder 단계에서만 쓰이고 최종 이미지에는 남지 않으므로, 실행 시에는 컨테이너 환경 변수로 다시 넣어야 합니다.
ARG BACKEND_API_BASE_URL
ARG AI_API_BASE_URL
# 하위 경로로 서비스할 때만 넣습니다. next.config.ts 가 basePath 로 굽습니다. (예: /test)
ARG BASE_PATH
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
