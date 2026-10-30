import urllib.request
import json

login_payload = json.dumps({"email": "admin@scanms.com", "password": "123456"}).encode('utf-8')
req = urllib.request.Request("http://localhost:8080/api/v1/auth/login", data=login_payload, headers={"Content-Type": "application/json"})
try:
    with urllib.request.urlopen(req) as resp:
        body = resp.read().decode('utf-8')
        print("Login status:", resp.status)
        print("Login body:", body)
        data = json.loads(body)
        token = data.get("result", {}).get("access_token") or data.get("access_token")
except Exception as e:
    print("Login error:", e)
    token = None

if token:
    endpoints = [
        "/users",
        "/users/admin/stats",
        "/stores/admin/stats",
        "/products/admin/stats",
        "/orders/admin/stats"
    ]
    for ep in endpoints:
        url = "http://localhost:8080/api/v1" + ep
        me_req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}", "x-skip-cache": "true"})
        try:
            with urllib.request.urlopen(me_req) as me_resp:
                print(f"GET {ep} status:", me_resp.status)
        except Exception as e:
            print(f"GET {ep} error:", e)
            if hasattr(e, 'read'):
                print(f"GET {ep} error body:", e.read().decode('utf-8'))
