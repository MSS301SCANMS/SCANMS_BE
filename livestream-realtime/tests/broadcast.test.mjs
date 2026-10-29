import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { io } from 'socket.io-client';
import { createLiveServer } from '../server.mjs';

test('publisher heartbeat and disconnect are signed, and reconnect uses a new publisher generation', { timeout: 12000 }, async () => {
  const key = 'fixture-broadcast-key-32-characters-long';
  const events = [], sockets = [];
  const session = { livestreamId: 'live', storeId: 'store', status: 'LIVE', productRefs: { productIds: [] }, collaboratorRefs: { collaboratorIds: ['kol'] }, participantRefs: { collaboratorResponses: { kol: 'ACCEPTED' } } };
  const server = createLiveServer({ metricsSecret: key, pollMs: 200,
    verifyToken: async token => ({ sub: token, exp: Math.floor(Date.now() / 1000) + 600, realm_access: { roles: token === 'host' ? ['KOL_CTV'] : ['USER'] } }),
    fetcher: async (url, request) => {
      if (url.endsWith('/broadcast')) {
        const input = JSON.parse(request.body), time = request.headers['X-Live-Metrics-Time'];
        assert.equal(request.headers['X-Live-Metrics-Signature'], createHmac('sha256', key).update(['BROADCAST', 'live', input.publisherId, input.epoch, input.sequence, input.state, time].join('\n')).digest('hex'));
        events.push(input);
        return Response.json({ result: { state: input.state } });
      }
      return Response.json({ result: url.endsWith('/collaborators/me') ? { collaboratorId: 'kol', approvalStatus: 'APPROVED' }
        : url.includes('/referrals/livestream-access?') ? { approvalStatus: 'APPROVED', collaboratorId: 'kol', storeId: 'store' }
        : url.endsWith('/purchase-access') ? { token: 'fixture' } : session });
    },
  });
  await new Promise(resolve => server.http.listen(0, '127.0.0.1', resolve));
  const connect = async token => {
    const socket = io(`http://127.0.0.1:${server.http.address().port}/live-stream`, { transports: ['websocket'], auth: { token }, reconnection: false });
    sockets.push(socket); await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('connect_error', reject); });
    return socket;
  };
  const emit = (socket, event, body) => socket.timeout(3000).emitWithAck(event, body);
  try {
    const host = await connect('host');
    assert.equal((await emit(host, 'join_live_room', { sessionId: 'live', requestedRole: 'host' })).ok, true);
    assert.equal((await emit(host, 'live_stream_started', { sessionId: 'live' })).ok, true);
    const generation = events.at(-1);
    host.disconnect();
    for (let attempt = 0; attempt < 30 && !events.some(event => event.state === 'RECONNECTING'); attempt++) await new Promise(resolve => setTimeout(resolve, 20));
    assert.ok(events.some(event => event.state === 'RECONNECTING' && event.publisherId === generation.publisherId));
    const replacement = await connect('host');
    await emit(replacement, 'join_live_room', { sessionId: 'live', requestedRole: 'host' });
    assert.equal((await emit(replacement, 'live_stream_started', { sessionId: 'live' })).ok, true);
    assert.notEqual(events.at(-1).publisherId, generation.publisherId);
    assert.ok(events.at(-1).epoch >= generation.epoch);
  } finally { sockets.forEach(socket => socket.disconnect()); await server.close(); }
});
