#!/usr/bin/env python3
"""Bounded, signed GitHub push hooks; credentials live outside the repository."""
import argparse
from collections import deque
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import queue
import signal
import subprocess
import threading
import time
from urllib.parse import urlparse

MAX_BODY = 1024 * 1024


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, code, **body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200 if self.path == "/health" else 404, ok=self.path == "/health")

    def do_POST(self):
        self.connection.settimeout(15)
        parsed = urlparse(self.path)
        name = parsed.path.strip("/")
        project = self.server.config["projects"].get(name)
        if not project:
            return self.reply(404, error="unknown project")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.reply(400, error="invalid length")
        if not 0 < length <= MAX_BODY:
            return self.reply(413, error="invalid body size")
        body = self.rfile.read(length)
        expected = "sha256=" + hmac.new(self.server.secret, body, hashlib.sha256).hexdigest()
        if parsed.query or not hmac.compare_digest(self.headers.get("X-Hub-Signature-256", ""), expected):
            return self.reply(401, error="bad signature")
        try:
            payload = json.loads(body)
            if not isinstance(payload, dict):
                raise ValueError()
        except (ValueError, UnicodeError):
            return self.reply(400, error="invalid json")
        event = self.headers.get("X-GitHub-Event", "")
        if event == "ping":
            return self.reply(200, ok=True)
        if event != "push" or payload.get("ref") != "refs/heads/" + project.get("branch", "main"):
            return self.reply(200, skipped=True)
        repository = payload.get("repository")
        if not project.get("repository") or not isinstance(repository, dict) or repository.get("full_name") != project["repository"]:
            return self.reply(403, error="repository mismatch")
        if payload.get("deleted"):
            return self.reply(200, skipped=True)
        delivery = self.headers.get("X-GitHub-Delivery", "")
        with self.server.guard:
            if delivery and delivery in self.server.deliveries:
                return self.reply(200, duplicate=True)
            try:
                self.server.tasks.put_nowait((name, project))
            except queue.Full:
                return self.reply(503, error="deploy queue full")
            if delivery:
                self.server.deliveries.append(delivery)
        self.reply(202, queued=True)


def worker(server):
    while True:
        name, project = server.tasks.get()
        try:
            directory = Path(server.config.get("log_dir", "/var/log/webhook-deploy"))
            directory.mkdir(mode=0o700, parents=True, exist_ok=True)
            filename = directory / (name + "-" + time.strftime("%Y%m%d-%H%M%S") + ".log")
            with filename.open("w") as log:
                process = subprocess.Popen(["bash", server.config["script"], name, project["dir"], project["branch"]], stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    result = process.wait(timeout=900)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                    result = 124
            print("Deployment", name, "exit", result, "log", filename, flush=True)
        except Exception as error:
            print("Deployment failed:", name, type(error).__name__, flush=True)
        finally:
            server.tasks.task_done()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("-c", "--config", default="/etc/webhook-deploy/config.json")
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text())
    secret = Path(config["secret_file"]).read_bytes().strip()
    if len(secret) < 32:
        raise ValueError("A private webhook signing key of at least 32 bytes is required")
    server = ThreadingHTTPServer((config.get("listen", "127.0.0.1"), config.get("port", 9000)), Handler)
    server.config, server.secret = config, secret
    server.tasks, server.deliveries, server.guard = queue.Queue(maxsize=8), deque(maxlen=1024), threading.Lock()
    threading.Thread(target=worker, args=(server,), daemon=True).start()
    print("Signed deployment hooks listening on loopback", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
