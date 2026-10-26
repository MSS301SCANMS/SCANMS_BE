import urllib.request
import json

accounts = [
    ("admin@scanms.com", "123456", "ADMIN"),
    ("shopowner01@scanms.com", "123456", "SHOP_OWNER"),
    ("kolctv01@scanms.com", "123456", "KOL_CTV"),
    ("manager01@scanms.com", "123456", "MANAGER"),
]

for email, pwd, expected_role in accounts:
    print(f"\n--- Testing account: {email} ({expected_role}) ---")
    payload = json.dumps({"email": email, "password": pwd}).encode('utf-8')
    req = urllib.request.Request(
        "http://localhost:8080/api/v1/auth/login",
        data=payload,
        headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            token = data.get("result", {}).get("access_token")
            print("  Login: SUCCESS (200)")
    except Exception as e:
        print("  Login: FAILED", e)
        continue

    # Test GET /users
    me_req = urllib.request.Request(
        "http://localhost:8080/api/v1/users",
        headers={"Authorization": f"Bearer {token}", "x-skip-cache": "true"}
    )
    try:
        with urllib.request.urlopen(me_req) as me_resp:
            me_data = json.loads(me_resp.read().decode('utf-8'))
            user = me_data.get("result", {})
            print(f"  GET /users: SUCCESS (200), userId={user.get('userId')}, roles={user.get('roles')}")
    except Exception as e:
        print("  GET /users: FAILED", e)
