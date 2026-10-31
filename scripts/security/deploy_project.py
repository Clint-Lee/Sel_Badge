#!/usr/bin/env python3
"""Build a separate release, require a fresh backup, then switch with rollback."""
import datetime
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

PROJECTS = {
    "badge": ("Libaoyun/Sel_Badge", "main", "badge-backend", "8088", "/api/apps/public"),
    "media-tools-hot": ("Clint-Lee/Media_Tools", "hotVideo", "media-tools-hot", "3001", "/"),
    "xiyou": ("Libaoyun/XiYouOnline", "develop", "xiyou-game", "8081", "/auth/badge"),
}


def run(*args, **kwargs):
    print("RUN", args[0], flush=True)
    return subprocess.run(args, check=True, **kwargs)


def health(port, route):
    for _ in range(60):
        result = subprocess.run(["curl", "--max-time", "3", "--silent", "--fail", "--output", "/dev/null", "http://127.0.0.1:" + port + route])
        if result.returncode == 0:
            return
        time.sleep(2)
    raise RuntimeError("Release health check failed")


def main():
    name = sys.argv[1]
    repository, branch, service, port, route = PROJECTS[name]
    lock = open("/run/antigravity-deploy.lock", "w")
    fcntl.flock(lock, fcntl.LOCK_EX)
    source = Path("/srv/deploy-source") / (name + ".git")
    source.parent.mkdir(parents=True, exist_ok=True)
    url = "https://github.com/" + repository + ".git"
    if not source.exists():
        run("git", "clone", "--bare", "--single-branch", "--branch", branch, url, str(source), timeout=120)
    run("git", "--git-dir=" + str(source), "fetch", "--force", "origin", branch + ":refs/heads/" + branch, timeout=90)
    sha = subprocess.check_output(["git", "--git-dir=" + str(source), "rev-parse", branch], text=True).strip()
    state = Path("/var/log/webhook-deploy/.state/" + name + ".sha")
    if state.exists() and state.read_text().strip() == sha and "--force" not in sys.argv:
        print("Already deployed", sha)
        return
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    base = Path("/srv/releases") / name
    release = base / (stamp + "-" + sha[:12])
    release.mkdir(parents=True)
    run("git", "clone", "--shared", "--no-checkout", str(source), str(release), timeout=60)
    run("git", "checkout", "--detach", sha, cwd=release)
    env = dict(os.environ, MAVEN_OPTS="-Xmx384m", NODE_OPTIONS="--max-old-space-size=384", PUPPETEER_SKIP_DOWNLOAD="true")
    if name == "badge":
        if "on-profile: prod" not in (release / "backend/src/main/resources/application.yml").read_text():
            raise RuntimeError("Refusing a release without the production key policy")
        run("bash", "mvnw", "-q", "package", "-Dkotlin.compiler.execution.strategy=in-process", cwd=release / "backend", env=env, timeout=600)
        run("npm", "ci", "--no-audit", "--no-fund", cwd=release / "frontend", env=env, timeout=180)
        run("npm", "run", "build", cwd=release / "frontend", env=env, timeout=180)
        command = "/usr/bin/java -Xms128m -Xmx256m -XX:+UseG1GC -jar " + str(release / "backend/target/badge-auth-platform-1.0.0.jar") + " --server.port=8088 --server.address=127.0.0.1"
        working = "/srv/repo/Sel_Badge/backend"
    else:
        if name == "media-tools-hot":
            if "const SIGNING_KEY" not in (release / "lib/guest-access.js").read_text():
                raise RuntimeError("Refusing a release without signed visitor identities")
            run("npm", "ci" if (release / "package-lock.json").exists() else "install", "--omit=dev", "--no-audit", "--no-fund", cwd=release, env=env, timeout=300)
            run("node", "/opt/antigravity/security-tools/test-media-guests.cjs", str(release / "lib/guest-access.js"), env=env)
        else:
            if "const publicPath =" not in (release / "server.js").read_text():
                raise RuntimeError("Refusing a release without private-path protection")
            run("node", "/opt/antigravity/security-tools/test-xiyou-http.cjs", str(release / "server.js"), env=env)
        command, working = "/usr/bin/node " + str(release / "server.js"), str(release)

    # A failed backup prevents switching. H2 is copied only after its owner stops.
    run("python3", "/opt/antigravity/backup_runtime.py", timeout=180)
    unit = Path("/etc/systemd/system/" + service + ".service")
    previous = unit.read_text()
    frontend = Path("/var/www/badge/current")
    old_frontend = str(frontend.resolve()) if name == "badge" else None
    old_release = Path("/etc/antigravity/" + name + "-release.path")
    old_path = old_release.read_text().strip() if old_release.exists() else None
    replacement = re.sub(r"^ExecStart=.*$", "ExecStart=" + command, previous, flags=re.MULTILINE)
    replacement = re.sub(r"^WorkingDirectory=.*$", "WorkingDirectory=" + working, replacement, flags=re.MULTILINE)
    try:
        unit.write_text(replacement)
        run("systemctl", "daemon-reload")
        run("systemctl", "restart", service)
        health(port, route)
        if name == "badge":
            temporary = frontend.with_name(".current.security-new")
            temporary.unlink(missing_ok=True)
            temporary.symlink_to(release / "frontend/dist", target_is_directory=True)
            os.replace(temporary, frontend)
        run("nginx", "-t")
        run("systemctl", "reload", "nginx")
        state.parent.mkdir(parents=True, exist_ok=True)
        state.write_text(sha + "\n")
        old_release.write_text(str(release) + "\n")
        os.chmod(old_release, 0o600)
    except Exception:
        unit.write_text(previous)
        run("systemctl", "daemon-reload")
        run("systemctl", "restart", service)
        if old_frontend:
            temporary = frontend.with_name(".current.security-rollback")
            temporary.unlink(missing_ok=True)
            temporary.symlink_to(old_frontend, target_is_directory=True)
            os.replace(temporary, frontend)
        health(port, route)
        raise
    for stale in sorted(base.iterdir(), key=lambda p: p.name, reverse=True)[5:]:
        if stale.is_dir() and stale.resolve().parent == base.resolve() and str(stale) not in (str(release), old_path):
            shutil.rmtree(stale)
    print(json.dumps({"deployed": name, "commit": sha, "release": str(release)}), flush=True)


if __name__ == "__main__":
    main()
