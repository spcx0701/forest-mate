# 숲길동무 API — 멀티스테이지(빌드 캐시 분리), 비루트 실행
FROM python@sha256:4d7dd74c5b2d3151fe58eb8ecc9c56be13471a240ce5423ba765b04f8f95ebe8 AS base
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PIP_NO_CACHE_DIR=1
WORKDIR /app

COPY requirements.lock .
RUN pip install --no-cache-dir --require-hashes -r requirements.lock

COPY server/ ./server/
COPY app/ ./app/

# 비루트 사용자 (컨테이너 하드닝)
RUN useradd -m -u 10001 forest && chown -R forest:forest /app
USER forest

EXPOSE 5181
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:5181/api/v1/healthz').status==200 else 1)"

# 단일 워커 = 인메모리 EventBus 일관성 보장. 다중 워커로 확장 시 Redis Pub/Sub로 교체.
# PORT 환경변수가 있으면 그 포트로(클라우드 호스팅: Render/Railway/Fly가 주입), 없으면 5181.
CMD ["sh", "-c", "uvicorn server.main:app --host 0.0.0.0 --port ${PORT:-5181}"]
