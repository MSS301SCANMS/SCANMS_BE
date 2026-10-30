import http from 'node:http';
import { pathToFileURL } from 'node:url';

const springPath = '/api/v1/payments/webhooks/payos';
const legacyPath = '/api/orders/payos/webhook';
export function createPayosWebhookRelay({ authority = process.env.FINANCE_AUTHORITY || 'LEGACY',
  springUrl = process.env.FINANCE_PAYMENT_URL || 'http://127.0.0.1:8084',
  legacyUrl = 'http://127.0.0.1:3000', fetcher = fetch } = {}) {
  if (!['SPRING', 'LEGACY'].includes(authority)) throw new Error('Unknown finance authority');
  return http.createServer(async (request, response) => {
    if (request.method !== 'POST' || ![springPath, legacyPath].includes(request.url)) {
      response.writeHead(404); response.end('Not found'); return;
    }
    if (!request.headers['content-type']?.includes('application/json')) {
      response.writeHead(415); response.end('JSON required'); return;
    }
    try {
      let size = 0; const chunks = [];
      for await (const chunk of request) {
        size += chunk.length;
        if (size > 128 * 1024) { response.writeHead(413); response.end(); return; }
        chunks.push(chunk);
      }
      // One money authority per callback; forward the original signed bytes.
      const upstream = await fetcher(authority === 'SPRING' ? springUrl + springPath : legacyUrl + legacyPath, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: Buffer.concat(chunks), signal: AbortSignal.timeout(15000),
      });
      response.writeHead(upstream.status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
      response.end(await upstream.text());
    } catch {
      response.writeHead(502); response.end('{"message":"Webhook backend unavailable"}');
    }
  });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.PAYOS_RELAY_PORT || 3301);
  createPayosWebhookRelay().listen(port, '127.0.0.1', () => console.log(`PayOS webhook relay listening on 127.0.0.1:${port}`));
}
