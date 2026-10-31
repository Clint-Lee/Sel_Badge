#!/usr/bin/env python3
"""Create a consistent protected baseline and rehearse rotation on a database copy."""
import datetime
import json
import os
from pathlib import Path
import secrets
import shlex
import shutil
import subprocess
import zipfile


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL)


def protected(path, value):
    path.write_text(value, encoding="utf-8")
    os.chmod(path, 0o600)


root = Path("/etc/antigravity")
root.mkdir(mode=0o700, exist_ok=True)
stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
baseline = Path("/var/backups/antigravity-security") / ("baseline-" + stamp)
baseline.mkdir(mode=0o700, parents=True)
environment = subprocess.check_output(["systemctl", "show", "badge-backend", "-p", "Environment", "--value"], text=True)
old = dict(entry.split("=", 1) for entry in shlex.split(environment) if "=" in entry)
protected(baseline / "old-badge.env", "\n".join(key + "=" + value for key, value in old.items()) + "\n")
protected(root / "rotation-old.env", "BADGE_CRYPTO_MASTER_KEY=" + old["BADGE_CRYPTO_MASTER_KEY"] + "\n")
badge_url, media_url, game_url = ("https://39.97.50.218:" + port for port in ("8080", "8180", "8280"))
next_env = {
    "BADGE_JWT_SECRET": secrets.token_urlsafe(64),
    "BADGE_CRYPTO_MASTER_KEY": secrets.token_urlsafe(48),
    "BADGE_CORS_ORIGINS": ",".join((badge_url, media_url, game_url)),
    "PUBLIC_BADGE_URL": badge_url,
    "PUBLIC_MEDIA_URL": media_url,
    "PUBLIC_GAME_URL": game_url,
    "SPRING_PROFILES_ACTIVE": "prod",
}
protected(root / "badge.env", "\n".join(key + "=" + value for key, value in next_env.items()) + "\n")
client_ids = ("app_portal_01", "app_hotpot_video", "app_xiyou_game", "app_game_arcade", "app_cloud_service", "app_wiki_knowledge")
clients = {key: secrets.token_urlsafe(32) for key in client_ids}
protected(root / "sso-clients.env", "\n".join(key + "=" + value for key, value in clients.items()) + "\n")
shared = {"NODE_ENV": "production", "HOST": "127.0.0.1", "BADGE_URL": "http://127.0.0.1:8088", "BADGE_PORTAL_URL": badge_url}
media = {**shared, "PORT": "3001", "HOTPOT_ALLOWED_HOSTS": "39.97.50.218,127.0.0.1,localhost", "HOTPOT_PUBLIC_URL": media_url, "BADGE_CLIENT_ID": "app_hotpot_video", "BADGE_CLIENT_SECRET": clients["app_hotpot_video"], "HOTPOT_GUEST_SECRET": secrets.token_urlsafe(48), "HOTPOT_GUEST_LEDGER": "/srv/repo/Media_Tools_Hot/.runtime/data/guest-quotas.json"}
game = {**shared, "PORT": "8081", "XIYOU_PUBLIC_URL": game_url, "BADGE_CLIENT_ID": "app_xiyou_game", "BADGE_CLIENT_SECRET": clients["app_xiyou_game"], "XIYOU_SESSION_SECRET": secrets.token_hex(32)}
for name, values in (("media.env", media), ("xiyou.env", game)):
    protected(root / name, "\n".join(key + "=" + value for key, value in values.items()) + "\n")

was_active = subprocess.run(["systemctl", "is-active", "--quiet", "badge-backend"]).returncode == 0
try:
    run("systemctl", "stop", "badge-backend")
    shutil.copytree("/srv/repo/Sel_Badge/backend/data", baseline / "badge-data")
    shutil.copy2("/etc/systemd/system/badge-backend.service", baseline / "badge-backend.service")
    for name, location in (("xiyou", "/srv/repo/XiYouOnline"), ("media", "/srv/repo/Media_Tools_Hot")):
        if name == "xiyou":
            run("pm2", "stop", "xiyou-game")
            try:
                shutil.copytree(Path(location) / "saves", baseline / "xiyou-saves")
            finally:
                run("pm2", "restart", "xiyou-game")
        else:
            run("pm2", "stop", "media-tools-hot")
            try:
                target = baseline / "media-state"
                target.mkdir()
                for relative in (".runtime/data", "db.json", ".env"):
                    path = Path(location) / relative
                    if path.is_dir():
                        shutil.copytree(path, target / relative)
                    elif path.is_file():
                        (target / relative).parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(path, target / relative)
            finally:
                run("pm2", "restart", "media-tools-hot")
    shutil.copytree("/etc/nginx/conf.d", baseline / "nginx")
    shutil.copytree("/etc/webhook-deploy", baseline / "webhook")
    if Path("/srv/ecosystem.config.js").exists():
        shutil.copy2("/srv/ecosystem.config.js", baseline / "ecosystem.config.js")
finally:
    if was_active:
        run("systemctl", "start", "badge-backend")

tools = Path("/opt/antigravity/security-tools")
tools.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile("/srv/repo/Sel_Badge/backend/target/badge-auth-platform-1.0.0.jar") as jar:
    entry = next(name for name in jar.namelist() if name.startswith("BOOT-INF/lib/h2-") and name.endswith(".jar"))
    (tools / "h2.jar").write_bytes(jar.read(entry))
rehearsal = baseline / "rotation-rehearsal"
shutil.copytree(baseline / "badge-data", rehearsal)
protected(root / "rotation-baseline.path", str(baseline) + "\n")
print(json.dumps({"baseline": str(baseline), "rehearsal": str(rehearsal), "protected_config": str(root)}))
