#!/usr/bin/env python3
"""Dependency-free dev preview server for the ForestMate web app.

Serves ``app/`` as static files and implements ``/api/v1/mountain-hero`` with the
same Wikimedia allowlist as ``server/routers/public.py`` — using only the Python
standard library. Other ``/api/*`` calls are proxied to a running backend
(default ``127.0.0.1:5181``); when that is unavailable the web app falls back to
its bundled local data.

Purpose: preview the PWA with real mountain photos without a virtualenv (useful
when disk is tight or the FastAPI backend cannot be (re)installed). This is a dev
aid only — not a production server.

    python3 scripts/dev_preview_server.py        # http://127.0.0.1:8770
    FM_PORT=9000 FM_BACKEND=http://127.0.0.1:8000 python3 scripts/dev_preview_server.py
"""
from __future__ import annotations

import http.server
import json
import os
import socketserver
import urllib.error
import urllib.parse
import urllib.request
from html import escape

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_DIR = os.path.join(ROOT, "app")
BACKEND = os.environ.get("FM_BACKEND", "https://forestmate.onrender.com").rstrip("/")
PORT = int(os.environ.get("PORT") or os.environ.get("FM_PORT") or "8770")
UA = "ForestMate-DevPreview/1.0"
ALLOWED_HOST = "upload.wikimedia.org"
ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_BYTES = 2_000_000


def _normalize(name: str) -> str:
    parts = " ".join((name or "").split()).split(" ")
    return (parts[0] if parts else "산")[:80] or "산"


def _allowed(url: str) -> bool:
    p = urllib.parse.urlparse(url or "")
    return p.scheme == "https" and p.netloc == ALLOWED_HOST and p.path.startswith("/wikipedia/")


def _fallback_svg(name: str, height: int) -> bytes:
    h = max(0, min(int(height or 0), 9999))
    title = escape(name or "산")
    top = "#2D6A4F" if h >= 1200 else "#40916C" if h >= 600 else "#52B788"
    sky = "#A8C7B5" if h >= 1200 else "#CDE7D4"
    label = f"{title} · {h}m" if h else title
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300" viewBox="0 0 600 300">'
        f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{sky}"/><stop offset="1" stop-color="#EAF4EC"/></linearGradient></defs>'
        f'<rect width="600" height="300" fill="url(#g)"/>'
        f'<polygon points="0,300 150,150 260,220 380,90 500,210 600,150 600,300" fill="{top}" opacity="0.9"/>'
        f'<polygon points="320,300 460,120 600,260 600,300" fill="{top}"/>'
        f'<text x="24" y="280" font-family="sans-serif" font-size="22" font-weight="800" fill="#1B4332">{label}</text>'
        f"</svg>"
    )
    return svg.encode("utf-8")


def _fetch_hero(name: str, height: int) -> tuple[bytes, str]:
    page = urllib.parse.quote(_normalize(name), safe="")
    try:
        req = urllib.request.Request(
            f"https://ko.wikipedia.org/api/rest_v1/page/summary/{page}",
            headers={"accept": "application/json", "user-agent": UA},
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.load(resp)
        src = data.get("thumbnail", {}).get("source")
        if isinstance(src, str) and _allowed(src):
            ireq = urllib.request.Request(
                src, headers={"user-agent": UA, "accept": "image/webp,image/png,image/jpeg"}
            )
            with urllib.request.urlopen(ireq, timeout=5) as iresp:
                ct = iresp.headers.get("content-type", "").split(";", 1)[0].strip().lower()
                if ct in ALLOWED_TYPES:
                    body = iresp.read(MAX_BYTES + 1)
                    if len(body) <= MAX_BYTES:
                        return body, ct
    except Exception:
        pass
    return _fallback_svg(name, height), "image/svg+xml"


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=APP_DIR, **kwargs)

    def _send_bytes(self, status: int, body: bytes, content_type: str, cache: str = "no-store") -> None:
        self.send_response(status)
        self.send_header("content-type", content_type)
        self.send_header("content-length", str(len(body)))
        self.send_header("cache-control", cache)
        self.end_headers()
        self.wfile.write(body)

    def _proxy(self, method: str) -> None:
        length = int(self.headers.get("content-length", 0) or 0)
        payload = self.rfile.read(length) if length else None
        req = urllib.request.Request(BACKEND + self.path, data=payload, method=method)
        ctype = self.headers.get("content-type")
        if ctype:
            req.add_header("content-type", ctype)
        req.add_header("user-agent", UA)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                self._send_bytes(resp.status, resp.read(), resp.headers.get("content-type", "application/json"))
        except urllib.error.HTTPError as exc:  # surface real backend status
            self._send_bytes(exc.code, exc.read() or b"", exc.headers.get("content-type", "text/plain"))
        except Exception:
            self._send_bytes(502, b'{"detail":"backend unavailable"}', "application/json")

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/api/v1/mountain-hero":
            q = urllib.parse.parse_qs(parsed.query)
            name = q.get("name", ["산"])[0]
            try:
                height = int(q.get("height", ["0"])[0])
            except ValueError:
                height = 0
            body, ct = _fetch_hero(name, height)
            self._send_bytes(200, body, ct, cache="public, max-age=3600")
            return
        if parsed.path.startswith("/api/"):
            self._proxy("GET")
            return
        super().do_GET()

    def do_POST(self):
        self._proxy("POST")

    def log_message(self, *args):  # quiet
        pass


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    with Server(("127.0.0.1", PORT), Handler) as srv:
        print(f"ForestMate dev preview → http://127.0.0.1:{PORT}  (backend {BACKEND})")
        srv.serve_forever()
