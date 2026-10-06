import http from 'node:http';

const path = '/api/orders/payos/webhook';
const server = http.createServer(async (request, response) => {
  if (request.method !== 'POST' || request.url !== path) {
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
    const body = Buffer.concat(chunks);
    if (process.env.FINANCE_AUTHORITY === 'SPRING') {
      const spring = await fetch('http://127.0.0.1:8084/api/v1/payments/webhooks/payos', {
        method: 'POST', headers: { 'content-type': 'application/json' }, body, signal: AbortSignal.timeout(15000),
      });
      if (!spring.ok) { response.writeHead(spring.status, { 'content-type': 'application/json' }); response.end(await spring.text()); return; }
    }
    const upstream = await fetch(`http://127.0.0.1:3000${path}`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body, signal: AbortSignal.timeout(15000),
    });
    response.writeHead(upstream.status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    response.end(await upstream.text());
  } catch {
    response.writeHead(502); response.end('{"message":"Webhook backend unavailable"}');
  }
});
server.listen(3301, '127.0.0.1', () => console.log('PayOS webhook relay listening on 127.0.0.1:3301'));
