"""관제 실시간 이벤트 버스 — 단일 인스턴스 인메모리 pub/sub.

수평 확장 시 Redis Pub/Sub 또는 NATS로 교체한다(인터페이스 동일 유지).
"""
import asyncio
import json
from typing import Any


class EventBus:
    def __init__(self) -> None:
        self._subscribers: set[asyncio.Queue] = set()

    def subscribe(self) -> asyncio.Queue:
        q: asyncio.Queue = asyncio.Queue(maxsize=100)
        self._subscribers.add(q)
        return q

    def unsubscribe(self, q: asyncio.Queue) -> None:
        self._subscribers.discard(q)

    def publish(self, event: dict[str, Any]) -> None:
        payload = json.dumps(event, ensure_ascii=False, default=str)
        for q in list(self._subscribers):
            try:
                q.put_nowait(payload)
            except asyncio.QueueFull:
                # 느린 구독자는 끊는다 — 관제 화면은 재접속 시 summary로 복구
                self._subscribers.discard(q)


bus = EventBus()
