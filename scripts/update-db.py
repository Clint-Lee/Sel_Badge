import zipfile
import subprocess
import glob
import os

print("Extracting H2 from spring jar...")
with zipfile.ZipFile('/srv/repo/Sel_Badge/backend/target/badge-auth-platform-1.0.0.jar') as z:
    for n in z.namelist():
        if 'h2-' in n and n.endswith('.jar'):
            z.extract(n, '/tmp')
            print("Extracted:", n)

h2_jars = glob.glob('/tmp/BOOT-INF/lib/h2-*.jar')
if not h2_jars:
    raise RuntimeError("H2 jar not found")

h2_jar = h2_jars[0]
print("Using H2 jar:", h2_jar)

sql = """
UPDATE app_clients SET homepage_url = 'http://39.97.50.218:91', redirect_uri = 'http://39.97.50.218:91/auth/badge/callback' WHERE client_id = 'app_hotpot_video';
UPDATE app_clients SET homepage_url = 'http://39.97.50.218:8081', redirect_uri = 'http://39.97.50.218:8081/auth/badge/callback' WHERE client_id = 'app_xiyou_game';
UPDATE app_clients SET homepage_url = 'http://39.97.50.218', redirect_uri = 'http://39.97.50.218/sso/callback' WHERE client_id = 'app_portal_01';
"""

with open('/tmp/update.sql', 'w') as f:
    f.write(sql)

print("Stopping badge-backend...")
subprocess.run(["systemctl", "stop", "badge-backend"], check=True)

print("Running SQL script on H2...")
cmd = [
    "java", "-cp", h2_jar,
    "org.h2.tools.RunScript",
    "-url", "jdbc:h2:file:/srv/repo/Sel_Badge/backend/data/badge_auth_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "-user", "sa",
    "-script", "/tmp/update.sql"
]
subprocess.run(cmd, check=True)

print("Starting badge-backend...")
subprocess.run(["systemctl", "start", "badge-backend"], check=True)
print("Done updating H2!")

