import urllib.request
import json
import uuid
import subprocess

req = urllib.request.Request(
    'http://localhost:8180/realms/master/protocol/openid-connect/token',
    data=b'client_id=admin-cli&grant_type=password&username=admin&password=admin'
)
token = json.loads(urllib.request.urlopen(req).read())['access_token']

# 1. Ensure roles in Keycloak
for rname in ['USER', 'KOL_CTV', 'SHOP_OWNER', 'MANAGER', 'ADMIN']:
    req_r = urllib.request.Request(
        'http://localhost:8180/admin/realms/scanms/roles',
        data=json.dumps({'name': rname}).encode('utf-8'),
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
        method='POST'
    )
    try:
        urllib.request.urlopen(req_r)
        print('Created Keycloak role:', rname)
    except Exception as e:
        print('Keycloak role already exists:', rname)

# 1.1 Ensure scanms-client service account has realm-management permissions (fix Google login 403)
try:
    req_clients = urllib.request.Request('http://localhost:8180/admin/realms/scanms/clients?clientId=scanms-client', headers={'Authorization': 'Bearer ' + token})
    clients = json.loads(urllib.request.urlopen(req_clients).read())
    if clients:
        c_id = clients[0]['id']
        req_sa = urllib.request.Request(f'http://localhost:8180/admin/realms/scanms/clients/{c_id}/service-account-user', headers={'Authorization': 'Bearer ' + token})
        sa_id = json.loads(urllib.request.urlopen(req_sa).read())['id']

        req_rm = urllib.request.Request('http://localhost:8180/admin/realms/scanms/clients?clientId=realm-management', headers={'Authorization': 'Bearer ' + token})
        rm_id = json.loads(urllib.request.urlopen(req_rm).read())[0]['id']

        req_rm_roles = urllib.request.Request(f'http://localhost:8180/admin/realms/scanms/clients/{rm_id}/roles', headers={'Authorization': 'Bearer ' + token})
        all_rm_roles = json.loads(urllib.request.urlopen(req_rm_roles).read())
        target_rm = [r for r in all_rm_roles if r['name'] in ['realm-admin', 'manage-users', 'view-users', 'query-users', 'impersonation']]

        req_assign_rm = urllib.request.Request(
            f'http://localhost:8180/admin/realms/scanms/users/{sa_id}/role-mappings/clients/{rm_id}',
            data=json.dumps(target_rm).encode('utf-8'),
            headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
            method='POST'
        )
        urllib.request.urlopen(req_assign_rm)
        print('Assigned realm-management roles to scanms-client service account successfully!')
except Exception as e:
    print('Notice on scanms-client permissions:', e)

test_users = [
    {
        'username': 'kolctv01',
        'email': 'kolctv01@scanms.com',
        'fullName': 'Le Van KOLCTV',
        'firstName': 'Le Van',
        'lastName': 'KOLCTV',
        'roles': ['USER', 'KOL_CTV'],
        'phone': '0911222333'
    },
    {
        'username': 'shopowner01',
        'email': 'shopowner01@scanms.com',
        'fullName': 'Nguyen Van ShopOwner',
        'firstName': 'Nguyen Van',
        'lastName': 'ShopOwner',
        'roles': ['USER', 'SHOP_OWNER'],
        'phone': '0922333444'
    },
    {
        'username': 'manager01',
        'email': 'manager01@scanms.com',
        'fullName': 'System Manager',
        'firstName': 'System',
        'lastName': 'Manager',
        'roles': ['USER', 'MANAGER'],
        'phone': '0933444555'
    },
    {
        'username': 'admin',
        'email': 'admin@scanms.com',
        'fullName': 'System Administrator',
        'firstName': 'System',
        'lastName': 'Administrator',
        'roles': ['USER', 'ADMIN'],
        'phone': '0944555666'
    }
]

role_map = {
    'ADMIN': '4b3d9dd3-a16e-4956-b72e-c99e828c57e4',
    'MANAGER': 'c06aaaea-1dc1-4fdb-bdd0-7d34ac4840ad',
    'USER': '3286c240-67cf-4068-878e-3285643a48c3',
    'KOL_CTV': '32368142-1147-41d9-b5f2-639c9ae52025',
    'SHOP_OWNER': '4ea22f2e-c1fa-4c93-bcd4-8897d1a89927'
}

sql_statements = []

for u in test_users:
    user_payload = {
        'username': u['username'],
        'email': u['email'],
        'enabled': True,
        'emailVerified': True,
        'firstName': u['firstName'],
        'lastName': u['lastName'],
        'credentials': [{'type': 'password', 'value': '123456', 'temporary': False}]
    }
    req_u = urllib.request.Request(
        'http://localhost:8180/admin/realms/scanms/users',
        data=json.dumps(user_payload).encode('utf-8'),
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
        method='POST'
    )
    try:
        urllib.request.urlopen(req_u)
        print('Created Keycloak user:', u['username'])
    except Exception as e:
        print('User already exists in Keycloak:', u['username'])

    # Get user id from Keycloak
    req_search = urllib.request.Request(
        f"http://localhost:8180/admin/realms/scanms/users?username={u['username']}",
        headers={'Authorization': 'Bearer ' + token}
    )
    res_search = json.loads(urllib.request.urlopen(req_search).read())
    if res_search:
        kc_uid = res_search[0]['id']
        u['identity_subject'] = kc_uid

        # Assign roles in Keycloak
        roles_to_assign = []
        for rname in u['roles']:
            req_role_info = urllib.request.Request(
                f'http://localhost:8180/admin/realms/scanms/roles/{rname}',
                headers={'Authorization': 'Bearer ' + token}
            )
            r_info = json.loads(urllib.request.urlopen(req_role_info).read())
            roles_to_assign.append({'id': r_info['id'], 'name': r_info['name']})

        req_assign = urllib.request.Request(
            f'http://localhost:8180/admin/realms/scanms/users/{kc_uid}/role-mappings/realm',
            data=json.dumps(roles_to_assign).encode('utf-8'),
            headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
            method='POST'
        )
        try:
            urllib.request.urlopen(req_assign)
            print('Assigned roles in Keycloak for', u['username'])
        except Exception as e:
            print('Error assigning roles:', e)

        # Generate SQL for user_db
        user_uuid = str(uuid.uuid4())
        sql_statements.append(f"""
INSERT INTO users (user_id, created_at, updated_at, email, full_name, identity_subject, status, phone_number, username)
VALUES ('{user_uuid}', NOW(), NOW(), '{u['email']}', '{u['fullName']}', '{kc_uid}', 'ACTIVE', '{u['phone']}', '{u['username']}')
ON CONFLICT (email) DO UPDATE SET identity_subject = '{kc_uid}', status = 'ACTIVE';
""")
        for rname in u['roles']:
            rid = role_map[rname]
            sql_statements.append(f"""
INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '{rid}' FROM users WHERE email = '{u['email']}'
ON CONFLICT DO NOTHING;
""")

full_sql = "\n".join(sql_statements)
with open('c:/HW/mss/SCANMS_BE/scripts/seed_users.sql', 'w', encoding='utf-8') as f:
    f.write(full_sql)

print('Executing SQL seed into scanms-user-db...')
subprocess.run(['docker', 'exec', '-i', 'scanms-user-db', 'psql', '-U', 'postgres', '-d', 'user_db'], input=full_sql.encode('utf-8'), check=True)
print('User DB seed completed successfully!')
