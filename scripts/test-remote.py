import urllib.request
import json

req = urllib.request.Request(
    "http://127.0.0.1:8080/api/auth/login",
    data=json.dumps({"username": "admin", "password": "Admin@123456"}).encode(),
    headers={"Content-Type": "application/json"}
)

with urllib.request.urlopen(req) as resp:
    res = json.loads(resp.read().decode())
    print("Login successful! User:", res["data"]["user"]["username"])
    token = res["data"]["accessToken"]

req2 = urllib.request.Request(
    "http://127.0.0.1:8080/api/apps",
    headers={"Authorization": "Bearer " + token}
)

with urllib.request.urlopen(req2) as resp2:
    apps = json.loads(resp2.read().decode())
    print("Registered Apps in Badge:")
    for a in apps["data"]:
        print(f" - [{a['clientId']}] {a['appName']}: home={a['homepageUrl']}, redirect={a['redirectUri']}")

