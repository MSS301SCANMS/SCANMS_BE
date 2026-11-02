import test from 'node:test';
import assert from 'node:assert/strict';
import { io } from 'socket.io-client';
import { createLiveServer } from '../server.mjs';

test('store chat authorizes each subscription/write, commits before events, retries and reconnects', { timeout: 15000 }, async () => {
  const messages = new Map(), sockets = [];
  let rejectWrites = false;
  const server = createLiveServer({ origins: [], verifyToken: async token => {
    if (!['buyer', 'owner', 'outsider'].includes(token)) throw new Error('Invalid token');
    return { sub: token, exp: Math.floor(Date.now() / 1000) + 3600 };
  }, fetcher: async (url, options) => {
    const user = options.headers.Authorization.slice(7), path = new URL(url).pathname;
    if (path.endsWith('/identity')) return Response.json({ result: { userId: user } });
    if (!['buyer', 'owner'].includes(user) || !path.includes('/conversation-a/')) return Response.json({}, { status: 403 });
    if (path.endsWith('/subscription')) return Response.json({ result: { id: 'conversation-a', participantIds: ['buyer', 'owner'] } });
    if (path.endsWith('/messages') && options.method === 'POST') {
      if (rejectWrites) return Response.json({}, { status: 503 });
      const body = JSON.parse(options.body);
      if (!messages.has(body.messageId)) messages.set(body.messageId, { id: body.messageId, conversationId: 'conversation-a', senderId: user, messageText: body.messageText, createdAt: new Date().toISOString() });
      return Response.json({ result: messages.get(body.messageId) });
    }
    return Response.json({ result: [...messages.values()] });
  } });
  await new Promise(resolve => server.http.listen(0, '127.0.0.1', resolve));
  const connect = async token => {
    const socket = io(`http://127.0.0.1:${server.http.address().port}/chat`, { auth: { token }, transports: ['websocket'], reconnection: false });
    sockets.push(socket);
    await new Promise((resolve, reject) => { socket.once('connect', resolve); socket.once('connect_error', reject); });
    return socket;
  };
  const send = (socket, event, payload) => socket.timeout(3000).emitWithAck(event, payload);
  try {
    const buyer = await connect('buyer'), owner = await connect('owner'), outsider = await connect('outsider');
    assert.equal((await send(outsider, 'join_conversation', { conversationId: 'conversation-a' })).ok, false);
    assert.equal((await send(outsider, 'send_message', { conversationId: 'conversation-a', messageId: 'bad', messageText: 'Intrusion' })).ok, false);
    assert.equal((await send(buyer, 'join_conversation', { conversationId: 'conversation-a', role: 'ADMIN' })).ok, true);
    let events = 0;
    outsider.on('new_message', () => { throw new Error('Conversation leaked'); });
    owner.on('new_message', message => { assert.ok(messages.has(message.id), 'write must be persisted first'); events++; });
    const received = new Promise(resolve => owner.once('new_message', resolve));
    const input = { conversationId: 'conversation-a', messageId: 'retry-1', messageText: 'Hello shop', senderId: 'owner' };
    assert.equal((await send(buyer, 'send_message', input)).ok, true);
    assert.equal((await received).senderId, 'buyer');
    assert.equal((await send(buyer, 'send_message', input)).ok, true);
    assert.equal(messages.size, 1);
    rejectWrites = true;
    assert.equal((await send(buyer, 'send_message', { ...input, messageId: 'retry-2' })).ok, false);
    assert.equal(messages.size, 1);
    owner.disconnect();
    const reconnected = await connect('owner');
    assert.equal((await send(reconnected, 'join_conversation', { conversationId: 'conversation-a' })).ok, true);
    assert.ok(events >= 1);
  } finally { sockets.forEach(socket => socket.disconnect()); await server.close(); }
});
