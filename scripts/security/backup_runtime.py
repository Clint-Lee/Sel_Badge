#!/usr/bin/env python3
"""Consistent private-state backup, encrypted for an off-server RSA recipient."""
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tarfile
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

os.umask(0o077)
lock = open("/run/antigravity-backup.lock", "w")
fcntl.flock(lock, fcntl.LOCK_EX)
root = Path("/var/backups/antigravity")
root.mkdir(mode=0o700, parents=True, exist_ok=True)
stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
target = root / ("runtime-" + stamp + ".tar.gz")
services = ["badge-backend", "media-tools-hot", "xiyou-game"]
active = [s for s in services if subprocess.run(["systemctl", "is-active", "--quiet", s]).returncode == 0]
paths = ["/srv/repo/Sel_Badge/backend/data", "/srv/repo/Media_Tools_Hot/.runtime/data",
         "/srv/repo/Media_Tools_Hot/db.json", "/srv/repo/XiYouOnline/saves", "/etc/antigravity",
         "/etc/nginx/conf.d", "/etc/webhook-deploy", "/etc/letsencrypt",
         "/etc/systemd/system/badge-backend.service", "/etc/systemd/system/media-tools-hot.service",
         "/etc/systemd/system/xiyou-game.service"]
try:
    subprocess.run(["systemctl", "stop", *active], check=True)
    with tarfile.open(target, "w:gz") as archive:
        for name in paths:
            if Path(name).exists():
                archive.add(name, arcname=name.lstrip("/"))
except Exception:
    target.unlink(missing_ok=True)
    raise
finally:
    if active:
        subprocess.run(["systemctl", "start", *active], check=True)

recipient = serialization.load_pem_public_key(Path("/etc/antigravity/backup-recipient.pem").read_bytes())
key, nonce = secrets.token_bytes(32), secrets.token_bytes(12)
encryptor = Cipher(algorithms.AES(key), modes.GCM(nonce)).encryptor()
output = target.with_suffix(target.suffix + ".enc")
digest = hashlib.sha256()
with target.open("rb") as source, output.open("wb") as destination:
    while chunk := source.read(1024 * 1024):
        digest.update(chunk)
        destination.write(encryptor.update(chunk))
    destination.write(encryptor.finalize())
wrapped = recipient.encrypt(key, padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
metadata = {"version": 1, "cipher": "AES-256-GCM", "keyWrap": "RSA-OAEP-SHA256",
            "wrappedKey": wrapped.hex(), "nonce": nonce.hex(), "tag": encryptor.tag.hex(),
            "plaintextSha256": digest.hexdigest(), "createdAt": stamp, "archive": output.name}
output.with_suffix(output.suffix + ".json").write_text(json.dumps(metadata, indent=2) + "\n")
# Keep the most recent 14 encrypted archives. Historic pre-rotation baselines are separate.
for stale in sorted(root.glob("runtime-*.tar.gz.enc"), reverse=True)[14:]:
    stale.unlink()
    stale.with_suffix(stale.suffix + ".json").unlink(missing_ok=True)
target.unlink()
print(json.dumps({"encryptedArchive": str(output), "plaintextSha256": digest.hexdigest()}))
