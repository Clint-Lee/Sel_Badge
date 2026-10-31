#!/usr/bin/env python3
"""Back up and contain private static paths before the full production repair."""
import datetime
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL)


stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
backup = Path("/var/backups/antigravity-security") / stamp
backup.mkdir(parents=True, mode=0o700)
os.chmod(backup.parent, 0o700)
configs = [Path("/etc/nginx/conf.d") / (name + ".conf") for name in ("badge", "mediatools", "xiyou")]
for path in configs:
    shutil.copy2(path, backup / path.name)

private_locations = """
    # Private application data must never be served as static assets.
    location ^~ /saves/ { return 404; }
    location = /saves { return 404; }
    location ^~ /node_modules/ { return 404; }
    location ^~ /scratch/ { return 404; }
    location = /server.js { return 404; }
    location = /package.json { return 404; }
    location = /package-lock.json { return 404; }
    location ~ (^|/)\\. { return 404; }
"""
try:
    for path in configs:
        content = path.read_text()
        content = content.replace("proxy_set_header Host $host;", "proxy_set_header Host $http_host;")
        content = content.replace("proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;", "proxy_set_header X-Forwarded-For $remote_addr;")
        if path.name == "xiyou.conf" and "# Private application data" not in content:
            content = content.replace("    # Block sensitive files", private_locations + "\n    # Block sensitive files", 1)
        path.write_text(content)
    run("nginx", "-t")
    run("systemctl", "reload", "nginx")
except Exception:
    for path in configs:
        shutil.copy2(backup / path.name, path)
    run("nginx", "-t")
    run("systemctl", "reload", "nginx")
    raise

# A publicly reachable session key must be replaced, invalidating old sessions.
key_file = Path("/srv/repo/XiYouOnline/saves/.session-secret")
if key_file.exists():
    shutil.copy2(key_file, backup / "xiyou-session-secret.old")
    os.chmod(backup / "xiyou-session-secret.old", 0o600)
    temporary = key_file.with_suffix(".new")
    temporary.write_text(secrets.token_hex(32))
    os.chmod(temporary, 0o600)
    os.replace(temporary, key_file)
    run("pm2", "restart", "xiyou-game")
print(json.dumps({"backup": str(backup), "nginx": "reloaded", "game_sessions": "rotated"}))
