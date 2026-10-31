import urllib.request
import json
import urllib.error

url_base = 'http://127.0.0.1:101/api/download?url=https://v.douyin.com/test_video_url_'
headers = {
    'x-fingerprint': 'test_guest_fp_antigravity_001',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
}

print("Testing unauthenticated guest quota on GET /api/download (limit: 3/day)...")
for i in range(1, 6):
    req_url = url_base + str(i)
    req = urllib.request.Request(req_url, headers=headers, method='GET')
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            print(f"Attempt {i}: HTTP {response.status} -> Download stream initiated")
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='ignore')
        print(f"Attempt {i}: HTTP {e.code} -> {body[:100]}")
    except Exception as e:
        print(f"Attempt {i}: Exception -> {e}")
