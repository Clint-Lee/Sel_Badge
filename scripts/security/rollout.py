#!/usr/bin/env python3
"""One-time production cutover. Run only after a successful copy-database rehearsal."""
import datetime
import json
import os
from pathlib import Path
import pwd
import secrets
import shutil
import subprocess
import time
import urllib.request
import zipfile


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def wait_http(url, acceptable=(200,)):
    for _ in range(90):
        try:
            with urllib.request.urlopen(url, timeout=3) as response:
                if response.status in acceptable:
                    return
        except Exception:
            time.sleep(2)
    raise RuntimeError("Health check failed: " + url)


def unit(name, user, directory, command, env, writable, memory):
    text = f"""[Unit]
Description={name}
After=network.target
[Service]
Type=simple
User={user}
Group={user}
WorkingDirectory={directory}
EnvironmentFile=/etc/antigravity/{env}.env
ExecStart={command}
Restart=on-failure
RestartSec=5
TimeoutStopSec=40
SuccessExitStatus=143
UMask=0077
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths={writable}
MemoryMax={memory}
StandardOutput=journal
StandardError=journal
[Install]
WantedBy=multi-user.target
"""
    Path("/etc/systemd/system/" + name + ".service").write_text(text)


config = Path("/etc/antigravity")
certificate = Path("/etc/letsencrypt/live/antigravity-ip/fullchain.pem")
if not certificate.exists():
    raise RuntimeError("Trusted production certificate required before cutover")
new_jar = Path("/opt/antigravity/badge-auth-platform-security.jar")
tools = Path("/opt/antigravity/security-tools")
with zipfile.ZipFile(new_jar) as jar:
    for prefix, target in (("BOOT-INF/lib/spring-security-crypto-", "spring-security-crypto.jar"),
                           ("BOOT-INF/lib/spring-jcl-", "spring-jcl.jar")):
        entry = next(name for name in jar.namelist() if name.startswith(prefix) and name.endswith(".jar"))
        (tools / target).write_bytes(jar.read(entry))
env_path = config / "badge.env"
if "RECOVERY_ADMIN_PASSWORD=" not in env_path.read_text():
    with env_path.open("a") as stream:
        stream.write("RECOVERY_ADMIN_PASSWORD=" + secrets.token_urlsafe(32) + "\n")
os.chmod(env_path, 0o600)
media_env = config / "media.env"
if "XDG_CONFIG_HOME=" not in media_env.read_text():
    with media_env.open("a") as stream:
        stream.write("XDG_CONFIG_HOME=/srv/repo/Media_Tools_Hot/.runtime/config\nPUPPETEER_CACHE_DIR=/srv/repo/Media_Tools_Hot/.cache/puppeteer\n")

for user in ("badge", "mediatools", "xiyou"):
    try:
        pwd.getpwnam(user)
    except KeyError:
        run("useradd", "--system", "--no-create-home", "--shell", "/usr/sbin/nologin", user)

stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
backup = Path("/var/backups/antigravity-security/cutover-" + stamp)
backup.mkdir(mode=0o700, parents=True)
badge = Path("/srv/repo/Sel_Badge/backend")
media = Path("/srv/repo/Media_Tools_Hot")
game = Path("/srv/repo/XiYouOnline")
jar_path = badge / "target/badge-auth-platform-1.0.0.jar"
shutil.copy2("/etc/systemd/system/badge-backend.service", backup / "badge-backend.service")
shutil.copy2(jar_path, backup / "badge-before.jar")
shutil.copytree("/etc/nginx/conf.d", backup / "nginx")

run("systemctl", "stop", "badge-backend")
run("pm2", "stop", "media-tools-hot", "xiyou-game", stdout=subprocess.DEVNULL)
try:
    shutil.copytree(badge / "data", backup / "badge-data")
    shutil.copytree(game / "saves", backup / "xiyou-saves")
    shutil.copytree(media / ".runtime/data", backup / "media-data")
    # Migration is transactional and refuses any encrypted field that cannot be decrypted.
    run("java", "-cp", str(tools) + ":" + str(tools / "h2.jar") + ":" + str(tools / "spring-security-crypto.jar") + ":" + str(tools / "spring-jcl.jar"),
        "com.badge.maintenance.RotateSecrets",
        "jdbc:h2:file:" + str(badge / "data/badge_auth_db") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        str(config / "rotation-old.env"), str(env_path), str(config / "sso-clients.env"))

    # Invalidate SSO sessions issued under the old public credentials.
    accounts = media / ".runtime/data/accounts.json"
    if accounts.exists():
        value = json.loads(accounts.read_text())
        value["sessions"] = {}
        accounts.write_text(json.dumps(value, ensure_ascii=False))
        os.chmod(accounts, 0o600)

    shutil.copy2(new_jar, jar_path)
    for user, locations in (("badge", [badge / "data"]), ("mediatools", [media / ".runtime", media / ".cache"]), ("xiyou", [game / "saves"])):
        for location in locations:
            location.mkdir(exist_ok=True, parents=True)
            run("chown", "-R", user + ":" + user, str(location))

    unit("badge-backend", "badge", str(badge), "/usr/bin/java -Xms128m -Xmx256m -XX:+UseG1GC -jar " + str(jar_path) + " --server.port=8088 --server.address=127.0.0.1", "badge", str(badge / "data"), "512M")
    unit("media-tools-hot", "mediatools", str(media), "/usr/bin/node " + str(media / "server.js"), "media", str(media / ".runtime") + " " + str(media / ".cache"), "512M")
    unit("xiyou-game", "xiyou", str(game), "/usr/bin/node " + str(game / "server.js"), "xiyou", str(game / "saves"), "192M")

    for name, port, aliases in (("badge", "8080", ["100"]), ("mediatools", "8180", ["105", "101"]), ("xiyou", "8280", ["106", "102"])):
        path = Path("/etc/nginx/conf.d/" + name + ".conf")
        text = path.read_text()
        text = text.replace("listen " + port + (" default_server" if name == "badge" else "") + ";", "listen " + port + " ssl" + (" default_server" if name == "badge" else "") + ";")
        for alias in aliases:
            text = text.replace("    listen " + alias + ";\n", "")
        tls = """
    ssl_certificate /etc/letsencrypt/live/antigravity-ip/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/antigravity-ip/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:AntigravitySSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;
""" + "    error_page 497 =308 https://39.97.50.218:" + port + "$request_uri;\n"
        text = text.replace("    server_name _;", "    server_name _;\n" + tls, 1)
        text += "\nserver {\n" + "".join("    listen " + a + ";\n" for a in aliases) + "    server_name _;\n    return 308 https://39.97.50.218:" + port + "$request_uri;\n}\n"
        path.write_text(text)
    default = Path("/etc/nginx/conf.d/default.conf")
    default.write_text(default.read_text().replace("return 301 http://$host:8080$request_uri;", "return 308 https://39.97.50.218:8080$request_uri;"))
    run("nginx", "-t")
    run("systemctl", "daemon-reload")
    run("systemctl", "start", "badge-backend", "media-tools-hot", "xiyou-game")
    wait_http("http://127.0.0.1:8088/api/apps/public")
    wait_http("http://127.0.0.1:3001/")
    # The game correctly redirects anonymous visitors; curl does not follow it.
    run("curl", "--fail", "--silent", "--output", "/dev/null", "http://127.0.0.1:8081/auth/badge")
    run("systemctl", "reload", "nginx")
    for port, route in (("8080", "/api/apps/public"), ("8180", "/"), ("8280", "/auth/badge")):
        run("curl", "--fail", "--silent", "--output", "/dev/null", "--resolve", "39.97.50.218:" + port + ":127.0.0.1", "https://39.97.50.218:" + port + route)
    run("systemctl", "enable", "badge-backend", "media-tools-hot", "xiyou-game")
    run("pm2", "delete", "media-tools-hot", "xiyou-game", stdout=subprocess.DEVNULL)
    run("pm2", "save", "--force", stdout=subprocess.DEVNULL)
    (config / "cutover-backup.path").write_text(str(backup) + "\n")
    os.chmod(config / "cutover-backup.path", 0o600)
    print("Production cutover succeeded; rollback snapshot:", backup)
except Exception:
    subprocess.run(["systemctl", "stop", "badge-backend", "media-tools-hot", "xiyou-game"])
    shutil.rmtree(badge / "data")
    shutil.copytree(backup / "badge-data", badge / "data")
    shutil.copy2(backup / "badge-before.jar", jar_path)
    shutil.copy2(backup / "badge-backend.service", "/etc/systemd/system/badge-backend.service")
    for path in (backup / "nginx").glob("*.conf"):
        shutil.copy2(path, Path("/etc/nginx/conf.d") / path.name)
    subprocess.run(["systemctl", "daemon-reload"])
    subprocess.run(["systemctl", "start", "badge-backend"])
    # The new visitor module also needs its signing key during rollback.
    env = dict(os.environ)
    for line in (config / "media.env").read_text().splitlines():
        if line.startswith("HOTPOT_GUEST_SECRET="):
            env["HOTPOT_GUEST_SECRET"] = line.split("=", 1)[1]
    subprocess.run(["pm2", "restart", "media-tools-hot", "xiyou-game", "--update-env"], env=env, stdout=subprocess.DEVNULL)
    subprocess.run(["nginx", "-t"], check=True)
    subprocess.run(["systemctl", "reload", "nginx"])
    print("Cutover failed and old service/database configuration was restored")
    raise
