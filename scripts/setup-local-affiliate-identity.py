"""Configure dedicated identities on the existing LOCAL Docker Keycloak; never print secrets.

Uses the container's existing bootstrap administrator, preserves existing client secrets,
and writes credentials only to Git-ignored backend .env files. No user roles are changed.
"""
import base64
import json
import subprocess
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://localhost:8180'
REALM = 'scanms'


def request(path, method='GET', body=None, token=None, form=False):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None:
        headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        body = (urlencode(body) if form else json.dumps(body)).encode()
    try:
        with urlopen(Request(BASE + path, data=body, headers=headers, method=method), timeout=15) as response:
            payload = response.read()
            return json.loads(payload) if payload else None
    except HTTPError as error:
        raise RuntimeError(f'Local identity request failed: HTTP {error.code} ({method} {path})') from None


def save_env(path, values):
    relative = str(path.relative_to(ROOT))
    if subprocess.run(['git', 'check-ignore', '-q', relative], cwd=ROOT).returncode:
        raise RuntimeError('Refusing to save credentials to an unignored file: ' + relative)
    lines = path.read_text(encoding='utf-8-sig').splitlines() if path.exists() else []
    remaining = dict(values)
    for index, line in enumerate(lines):
        key = line.split('=', 1)[0].strip()
        if key in values:
            lines[index] = key + '=' + values[key]
            remaining.pop(key, None)
    lines.extend(key + '=' + value for key, value in remaining.items())
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def main():
    raw = json.loads(subprocess.check_output(['docker', 'inspect', 'keycloak-keycloak-1']))[0]
    env = dict(entry.split('=', 1) for entry in raw['Config']['Env'] if '=' in entry)
    username = env.get('KC_BOOTSTRAP_ADMIN_USERNAME') or env.get('KEYCLOAK_ADMIN')
    password = env.get('KC_BOOTSTRAP_ADMIN_PASSWORD') or env.get('KEYCLOAK_ADMIN_PASSWORD')
    if not username or not password:
        raise RuntimeError('Existing local Docker bootstrap administrator is unavailable')
    admin = request('/realms/master/protocol/openid-connect/token', 'POST', {
        'grant_type': 'password', 'client_id': 'admin-cli', 'username': username, 'password': password,
    }, form=True)['access_token']
    root_path = '/admin/realms/' + REALM
    token_url = BASE + '/realms/' + REALM + '/protocol/openid-connect/token'
    shared = {'FINANCE_TOKEN_URL': token_url, 'PAYMENT_SERVICE_TOKEN_URL': token_url}
    for module, client_id, prefix, role_name in [
        ('order-service', 'order-service', 'ORDER_SERVICE', 'ORDER_INTERNAL'),
        ('promotion-affiliate-service', 'promotion-affiliate-service', 'AFFILIATE_SERVICE', 'AFFILIATE_INTERNAL'),
        ('payment-service', 'payment-service', 'PAYMENT_SERVICE', 'PAYMENT_INTERNAL'),
    ]:
        clients = request(root_path + '/clients?' + urlencode({'clientId': client_id}), token=admin)
        if not clients:
            request(root_path + '/clients', 'POST', {'clientId': client_id, 'enabled': True,
                'publicClient': False, 'serviceAccountsEnabled': True, 'standardFlowEnabled': False,
                'implicitFlowEnabled': False, 'directAccessGrantsEnabled': False, 'protocol': 'openid-connect'}, admin)
            clients = request(root_path + '/clients?' + urlencode({'clientId': client_id}), token=admin)
        client = clients[0]
        if client.get('publicClient') or not client.get('serviceAccountsEnabled') or not client.get('enabled'):
            raise RuntimeError('Existing client must be an enabled confidential service account: ' + client_id)
        role_list = request(root_path + '/roles', token=admin)
        if not any(role['name'] == role_name for role in role_list):
            request(root_path + '/roles', 'POST', {'name': role_name}, admin)
        role = request(root_path + '/roles/' + role_name, token=admin)
        service_user = request(root_path + '/clients/' + client['id'] + '/service-account-user', token=admin)
        request(root_path + '/users/' + service_user['id'] + '/role-mappings/realm', 'POST', [role], admin)
        secret = request(root_path + '/clients/' + client['id'] + '/client-secret', token=admin)['value']
        token = request('/realms/' + REALM + '/protocol/openid-connect/token', 'POST', {
            'grant_type': 'client_credentials', 'client_id': client_id, 'client_secret': secret,
        }, form=True)['access_token']
        claims = json.loads(base64.urlsafe_b64decode(token.split('.')[1] + '=='))
        if role_name not in claims.get('realm_access', {}).get('roles', []):
            raise RuntimeError('Service token is missing its dedicated role: ' + client_id)
        settings = {'FINANCE_TOKEN_URL': token_url, 'PAYMENT_SERVICE_TOKEN_URL': token_url,
                    prefix + '_CLIENT_ID': client_id, prefix + '_CLIENT_SECRET': secret}
        save_env(ROOT / module / '.env', settings)
        shared.update(settings)
        print(client_id + ': dedicated service token verified (' + role_name + ')')
    save_env(ROOT / '.env', shared)
    print('Saved local backend credentials to ignored .env files. Restart the three services to load them.')


if __name__ == '__main__':
    main()
