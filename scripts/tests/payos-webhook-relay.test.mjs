import test from 'node:test';
import assert from 'node:assert/strict';
import { createPayosWebhookRelay } from '../payos-webhook-relay.mjs';

test('Spring callbacks preserve bytes and return once without calling legacy money authority', async () => {
  const calls = [];
  const server = createPayosWebhookRelay({ authority: 'SPRING', fetcher: async (url, request) => {
    calls.push({ url, body: request.body.toString() }); return Response.json({ code: '00' });
  } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const url = `http://127.0.0.1:${server.address().port}`;
    const body = '{"data":{"amount":1000}, "signature":"signed-original"}';
    for (const path of ['/api/v1/payments/webhooks/payos', '/api/orders/payos/webhook']) {
      const response = await fetch(url + path, { method: 'POST', headers: { 'content-type': 'application/json' }, body });
      assert.equal(response.status, 200); assert.deepEqual(await response.json(), { code: '00' });
    }
    assert.equal(calls.length, 2);
    assert.ok(calls.every(call => call.url === 'http://127.0.0.1:8084/api/v1/payments/webhooks/payos' && call.body === body));
    assert.equal((await fetch(url + '/api/v1/finance/wallets/me')).status, 404);
    assert.equal(calls.length, 2, 'the tunnel must expose no other backend routes');
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('invalid signatures retain backend rejection and non-JSON input is never forwarded', async () => {
  let forwards = 0;
  const server = createPayosWebhookRelay({ authority: 'SPRING', fetcher: async () => { forwards++; return Response.json({ message: 'Invalid signature' }, { status: 400 }); } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const url = `http://127.0.0.1:${server.address().port}/api/v1/payments/webhooks/payos`;
    assert.equal((await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' })).status, 400);
    assert.equal((await fetch(url, { method: 'POST', body: 'text' })).status, 415);
    assert.equal(forwards, 1);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
