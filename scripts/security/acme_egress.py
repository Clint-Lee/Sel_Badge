#!/usr/bin/env python3
"""Loopback-only ACME CONNECT relay with a fragmented TLS ClientHello.

Some upstream paths stall on a single-record ClientHello. TLS records may split
handshake messages without changing the handshake or its certificate checks.
Only the two official Let's Encrypt ACME hosts on port 443 are permitted.
"""
import select
import time
import socket
import socketserver

ALLOWED = {"acme-v02.api.letsencrypt.org", "acme-staging-v02.api.letsencrypt.org"}


def exact(connection, count):
    output = bytearray()
    while len(output) < count:
        chunk = connection.recv(count - len(output))
        if not chunk:
            raise ConnectionError("Early close")
        output.extend(chunk)
    return bytes(output)


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(30)
        header = bytearray()
        while not header.endswith(b"\r\n\r\n") and len(header) < 8192:
            header.extend(exact(self.request, 1))
        line = header.split(b"\r\n", 1)[0].decode("ascii")
        method, target, _ = line.split(" ", 2)
        host, port = target.rsplit(":", 1)
        if method != "CONNECT" or host not in ALLOWED or port != "443":
            self.request.sendall(b"HTTP/1.1 403 Forbidden\r\n\r\n")
            return
        with socket.create_connection((host, 443), timeout=15) as upstream:
            upstream.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            self.request.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            record = exact(self.request, 5)
            length = int.from_bytes(record[3:5], "big")
            if record[0] != 22 or length > 65535:
                return
            payload = exact(self.request, length)
            position = payload.find(host.encode("ascii"))
            split = position + len(host) // 2 if position >= 0 else length // 2
            for fragment in (payload[:split], payload[split:]):
                upstream.sendall(record[:3] + len(fragment).to_bytes(2, "big") + fragment)
                time.sleep(0.15)
            peers = (self.request, upstream)
            while True:
                readable, _, _ = select.select(peers, [], [], 60)
                if not readable:
                    return
                for source in readable:
                    data = source.recv(65536)
                    if not data:
                        return
                    (upstream if source is self.request else self.request).sendall(data)


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    with Server(("127.0.0.1", 19091), Handler) as server:
        server.serve_forever()
