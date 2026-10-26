import test from 'node:test';
import assert from 'node:assert/strict';
import { io } from 'socket.io-client';
import { generateKeyPair, SignJWT, jwtVerify } from 'jose';
import { createLiveServer } from '../server.mjs';

test('signed identity, host consent, room isolation, chat identity, pinning and ending a live session', { timeout: 20000 }, async () => {
  const { privateKey, publicKey } = await generateKeyPair('RS256');
  const token = async (sub, roles, expires = '5m') => new SignJWT({ realm_access: { roles }, name: sub }).setProtectedHeader({ alg: 'RS256' }).setSubject(sub).setIssuer('fixture').setExpirationTime(expires).sign(privateKey);
  const credentials = {
    host: await token('kol-user', ['KOL_CTV']), viewer: await token('buyer', ['USER']),
    other: await token('other-kol', ['KOL_CTV']), shop: await token('shop-owner', ['SHOP_OWNER']),
    expired: await token('expired', ['USER'], '-1s'),
  };
  const sessions = new Map(['room-a','room-b','scheduled'].map(id => [id, { livestreamId: id, storeId: 'store-a', status: id === 'scheduled' ? 'SCHEDULED' : 'LIVE', productRefs: { productIds: ['shirt','dress'] }, collaboratorRefs: { collaboratorIds: ['kol-a'] }, participantRefs: { collaboratorResponses: { 'kol-a': 'ACCEPTED' } } }]));
  const server = createLiveServer({ pollMs: 100, verifyToken: async token => (await jwtVerify(token, publicKey, { issuer: 'fixture' })).payload,
    fetcher: async (url, options) => {
      const { payload } = await jwtVerify(options.headers.Authorization.slice(7), publicKey);
      let result;
      if (url.endsWith('/collaborators/me')) result = { collaboratorId: payload.sub === 'kol-user' ? 'kol-a' : 'other-kol', approvalStatus: 'APPROVED' };
      else if (url.includes('/referrals/livestream-access?')) result = { approvalStatus: 'APPROVED', collaboratorId: 'kol-a', storeId: 'store-a' };
      else if (url.endsWith('/stores/mine')) result = payload.sub === 'shop-owner' ? [{ storeId: 'store-a', approvalStatus: 'APPROVED' }] : [];
      else result = sessions.get(url.split('/').at(-1));
      return Response.json({ result }, { status: result ? 200 : 404 });
    },
  });
  await new Promise(resolve => server.http.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.http.address().port}/live-stream`, sockets = [];
  const connect = async (token, success = true) => {
    const socket = io(url, { auth: { token }, transports: ['websocket'], reconnection: false }); sockets.push(socket);
    return new Promise((resolve, reject) => {
      socket.once('connect', () => success ? resolve(socket) : reject(Error('unexpected connection')));
      socket.once('connect_error', error => success ? reject(error) : resolve(error));
    });
  };
  const emit = (socket, event, payload) => socket.timeout(2000).emitWithAck(event, payload);
  const once = (socket, event) => new Promise(resolve => socket.once(event, resolve));
  try {
    assert.match((await connect('forged-token', false)).message, /đăng nhập/);
    assert.match((await connect(credentials.expired, false)).message, /đăng nhập/);
    const host = await connect(credentials.host), viewer = await connect(credentials.viewer), outsider = await connect(credentials.other);
    assert.equal((await emit(outsider, 'join_live_room', { sessionId: 'room-a', requestedRole: 'host' })).ok, false, 'uninvited KOL cannot host');
    assert.equal((await emit(viewer, 'join_live_room', { sessionId: 'room-a', requestedRole: 'host' })).ok, false, 'a customer cannot self-assign host');
    assert.equal((await emit(host, 'join_live_room', { sessionId: 'room-a', requestedRole: 'host' })).ok, true);
    assert.equal((await emit(viewer, 'join_live_room', { sessionId: 'room-a', requestedRole: 'viewer' })).hostOnline, true);
    await emit(outsider, 'join_live_room', { sessionId: 'room-b', requestedRole: 'viewer' });
    await emit(host, 'live_stream_started', { sessionId: 'room-a' });
    assert.equal((await emit(viewer, 'live_stream_started', { sessionId: 'room-a' })).ok, false);
    assert.equal((await emit(host, 'live_signal', { sessionId: 'room-a', targetSocketId: outsider.id, signal: { description: { type: 'offer', sdp: 'fixture' } } })).ok, false);
    const signal = once(viewer, 'live_signal');
    assert.equal((await emit(host, 'live_signal', { sessionId: 'room-a', targetSocketId: viewer.id, signal: { description: { type: 'offer', sdp: 'fixture' } } })).ok, true);
    assert.equal((await signal).fromSocketId, host.id);
    const chat = once(host, 'new_live_chat');
    await emit(viewer, 'send_live_chat', { sessionId: 'room-a', text: 'Hello', sender: 'ADMIN', role: 'HOST' });
    const message = await chat;assert.equal(message.sender, 'buyer');assert.equal(message.role, 'VIEWER');
    assert.equal((await emit(viewer, 'host_pin_product', { sessionId: 'room-a', productIndex: 1, productId: 'dress' })).ok, false);
    assert.equal((await emit(host, 'host_pin_product', { sessionId: 'room-a', productIndex: 1, productId: 'wrong' })).ok, false);
    const pin = once(viewer, 'product_pinned_changed');
    assert.equal((await emit(host, 'host_pin_product', { sessionId: 'room-a', productIndex: 1, productId: 'dress' })).ok, true);
    assert.equal((await pin).productId, 'dress');
    assert.equal((await emit(host, 'refresh_live_auth', { token: credentials.viewer })).ok, false, 'token rotation cannot switch identity');
    assert.equal((await emit(host, 'refresh_live_auth', { token: await token('kol-user', ['KOL_CTV']) })).ok, true);
    const ended = once(viewer, 'live_session_ended');sessions.get('room-a').status = 'ENDED';await ended;
    assert.equal(server.rooms.has('room-a'), false);
    const scheduledHost = await connect(credentials.shop);
    assert.equal((await emit(scheduledHost, 'join_live_room', { sessionId: 'scheduled', requestedRole: 'host' })).ok, true);
    assert.equal((await emit(scheduledHost, 'live_stream_started', { sessionId: 'scheduled' })).ok, false, 'camera cannot publish before shop starts the session');
    sessions.get('scheduled').status = 'LIVE';
    assert.equal((await emit(scheduledHost, 'live_stream_started', { sessionId: 'scheduled' })).ok, true);
  } finally { sockets.forEach(socket => socket.disconnect());await server.close(); }
});
