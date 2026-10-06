import { createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify, timingSafeEqual } from 'node:crypto';
import { mkdirSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { ContractError } from './rules.mjs';
export function identity(directory, issuer) {
  mkdirSync(directory, { recursive: true });
  const file = join(directory, 'identity-private.pem');
  if (!existsSync(file)) {
    const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
    writeFileSync(file, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600, flag: 'wx' });
  }
  const key = createPrivateKey(readFileSync(file)); const pub = createPublicKey(key);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const jwk = { ...pub.export({ format: 'jwk' }), kid: 'scanms-commerce', alg: 'RS256', use: 'sig' };
  return {
    jwks: { keys: [jwk] },
    token(user) {
      const now = Math.floor(Date.now() / 1000);
      const header = encode({ alg: 'RS256', kid: jwk.kid, typ: 'JWT' });
      const payload = encode({ iss: issuer, aud: 'scanms-finance', sub: user.id, iat: now, exp: now + 60, realm_access: { roles: [user.role] } });
      const input = `${header}.${payload}`;
      return `${input}.${sign('RSA-SHA256', Buffer.from(input), key).toString('base64url')}`;
    },
    verify(token) {
      try {
        const [h, p, s, extra] = token.split('.');
        if (!h || !p || !s || extra) throw new Error();
        const header = JSON.parse(Buffer.from(h, 'base64url'));
        const claims = JSON.parse(Buffer.from(p, 'base64url'));
        if (header.alg !== 'RS256' || header.kid !== jwk.kid || !verify('RSA-SHA256', Buffer.from(`${h}.${p}`), pub, Buffer.from(s, 'base64url')) ||
            claims.iss !== issuer || claims.aud !== 'scanms-finance' || claims.exp <= Date.now()/1000 || claims.iat > Date.now()/1000+5 || !claims.sub) throw new Error();
        return claims;
      } catch { throw new ContractError('Invalid finance identity', 401); }
    },
  };
}
export function equalSecret(actual, expected) {
  if (!actual || !expected) return false;
  const a = Buffer.from(actual), b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}
