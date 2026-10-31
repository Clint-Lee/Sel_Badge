import glob
import subprocess
import zipfile
import sys

h2_jars = glob.glob('/tmp/BOOT-INF/lib/h2-*.jar')
if not h2_jars:
    print("Extracting H2 from spring jar...")
    with zipfile.ZipFile('/srv/repo/Sel_Badge/backend/target/badge-auth-platform-1.0.0.jar') as z:
        for n in z.namelist():
            if 'h2-' in n and n.endswith('.jar'):
                z.extract(n, '/tmp')
    h2_jars = glob.glob('/tmp/BOOT-INF/lib/h2-*.jar')

if not h2_jars:
    print("ERROR: H2 jar not found")
    sys.exit(1)

h2_jar = h2_jars[0]
print("Using H2 jar:", h2_jar)

sql = """
UPDATE app_clients SET homepage_url = 'http://39.97.50.218:100', redirect_uri = 'http://39.97.50.218:100/sso/callback' WHERE client_id = 'app_portal_01';
UPDATE app_clients SET homepage_url = 'http://39.97.50.218:101', redirect_uri = 'http://39.97.50.218:101/auth/badge/callback' WHERE client_id = 'app_hotpot_video';
UPDATE app_clients SET homepage_url = 'http://39.97.50.218:102', redirect_uri = 'http://39.97.50.218:102/auth/badge/callback' WHERE client_id = 'app_xiyou_game';
SELECT client_id, app_name, homepage_url, redirect_uri FROM app_clients;
"""

with open('/tmp/update_100_102.sql', 'w', encoding='utf-8') as f:
    f.write(sql)

print("Stopping badge-backend...")
subprocess.run(['systemctl', 'stop', 'badge-backend'], check=True)

print("Running H2 update script...")
res = subprocess.run([
    'java', '-cp', h2_jar,
    'org.h2.tools.RunScript',
    '-url', 'jdbc:h2:file:/srv/repo/Sel_Badge/backend/data/badge_auth_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE',
    '-user', 'sa',
    '-script', '/tmp/update_100_102.sql',
    '-showResults'
], capture_output=True, text=True)

print("STDOUT:\n", res.stdout)
print("STDERR:\n", res.stderr)

print("Starting badge-backend...")
subprocess.run(['systemctl', 'start', 'badge-backend'], check=True)
print("Update finished successfully!")
