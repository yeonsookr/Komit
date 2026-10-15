# syntax=docker/dockerfile:1

FROM python:3.12-slim

WORKDIR /app

# 파이썬이 .pyc를 남기지 않고, 로그가 버퍼에 갇히지 않게 한다.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# 의존성만 먼저 넣어 레이어를 캐시한다. 소스만 바뀌면 다시 받지 않는다.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# 모듈은 평면이라 최상위 .py만 넣는다. scripts/ 와 tests/ 는 이미지에 넣지 않는다.
COPY *.py ./

RUN groupadd --system --gid 10001 komit \
    && useradd --system --uid 10001 --gid komit komit \
    && chown -R komit:komit /app

USER komit

EXPOSE 8080

ENTRYPOINT ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8080"]
