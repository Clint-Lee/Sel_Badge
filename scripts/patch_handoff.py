with open('d:/Extends/antigravityProj/Badge/PRODUCTION_DEPLOYMENT_HANDOFF.md', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace("100 端口登录", "8080 端口登录")
text = text.replace("http://39.97.50.218:100", "http://39.97.50.218:8080")
text = text.replace("http://39.97.50.218:101", "http://39.97.50.218:8180")
text = text.replace("http://39.97.50.218:102", "http://39.97.50.218:8280")
text = text.replace("http://39.97.50.218:105", "http://39.97.50.218:8180")
text = text.replace("http://39.97.50.218:106", "http://39.97.50.218:8280")

with open('d:/Extends/antigravityProj/Badge/PRODUCTION_DEPLOYMENT_HANDOFF.md', 'w', encoding='utf-8') as f:
    f.write(text)

print("Updated PRODUCTION_DEPLOYMENT_HANDOFF.md successfully")
