import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { Server } from 'socket.io';
import { createRemoteJWKSet, jwtVerify } from 'jose';

const idPattern = /^[A-Za-z0-9_-]{1,100}$/;
const emojis = new Set(['❤️','👍','😂','😮','😢','😡','🔥','🎁','🎉','💯','⭐']);
const unpack = body => body?.result ?? body?.data ?? body;
export function createLiveServer({
  apiUrl = process.env.API_URL || 'http://localhost:8080/api/v1',
  issuer = process.env.KEYCLOAK_ISSUER || 'http://localhost:8180/realms/scanms',
  origins = (process.env.ALLOWED_ORIGINS || 'http://localhost:5173,http://127.0.0.1:5173').split(',').map(value => value.trim()),
  maxViewers = Number(process.env.MAX_VIEWERS_PER_ROOM || 20),
  pollMs = 10000,
  verifyToken,
  fetcher = fetch,
} = {}) {
  const jwks = createRemoteJWKSet(new URL(`${issuer}/protocol/openid-connect/certs`));
  const verify = verifyToken || (async token => (await jwtVerify(token, jwks, {
    issuer, algorithms: ['RS256'], requiredClaims: ['sub', 'exp'],
  })).payload);
  const http = createServer((request, response) => {
    response.writeHead(request.url === '/health' ? 200 : 404, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify(request.url === '/health' ? { status: 'UP', service: 'livestream-realtime' } : { message: 'Not found' }));
  });
  const io = new Server(http, {
    cors: { origin: origins, credentials: true }, maxHttpBufferSize: 131072,
    allowRequest: (request, callback) => callback(null, !request.headers.origin || origins.includes(request.headers.origin)),
  });
  const live = io.of('/live-stream');
  const rooms = new Map();
  const read = async (path, token) => {
    const response = await fetcher(`${apiUrl}${path}`, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(6000) });
    if (!response.ok) throw new Error('Không xác minh được quyền hoặc trạng thái phiên live.');
    return unpack(await response.json());
  };
  const validIdentity = socket => {
    if (!socket.data.claims?.exp || socket.data.claims.exp * 1000 <= Date.now()) throw new Error('Phiên đăng nhập đã hết hạn.');
  };
  const authorize = async (socket, id, role) => {
    validIdentity(socket);
    const session = await read(`/livestreams/${id}`, socket.data.token);
    if ((session.livestreamId || session.id) !== id || !['LIVE','SCHEDULED'].includes(session.status)) throw new Error('Phiên livestream đã kết thúc hoặc không tồn tại.');
    if (role === 'host') {
      const realmRoles = socket.data.claims.realm_access?.roles || [];
      let authorized = false;
      if (realmRoles.includes('KOL_CTV')) {
        const profile = await read('/collaborators/me', socket.data.token);
        authorized = profile?.approvalStatus === 'APPROVED'
          && session.collaboratorRefs?.collaboratorIds?.includes(profile.collaboratorId)
          && session.participantRefs?.collaboratorResponses?.[profile.collaboratorId] === 'ACCEPTED';
        if (authorized) {
          const partnership = await read(`/referrals/livestream-access?storeId=${encodeURIComponent(session.storeId)}&collaboratorId=${encodeURIComponent(profile.collaboratorId)}`, socket.data.token);
          authorized = partnership?.approvalStatus === 'APPROVED' && partnership.collaboratorId === profile.collaboratorId && partnership.storeId === session.storeId;
        }
      }
      if (!authorized && realmRoles.includes('SHOP_OWNER')) {
        const stores = await read('/stores/mine', socket.data.token);
        authorized = Array.isArray(stores) && stores.some(store => (store.storeId || store.id) === session.storeId && store.approvalStatus === 'APPROVED');
      }
      if (!authorized) throw new Error('Chỉ chủ shop hoặc KOL đã chấp nhận lời mời của phiên mới được phát sóng.');
    }
    return session;
  };
  const roomFor = socket => {
    validIdentity(socket);
    const room = rooms.get(socket.data.sessionId);
    if (!room || !room.members.has(socket.id)) throw new Error('Bạn chưa tham gia phòng live.');
    return room;
  };
  const emitCounts = room => live.to(room.id).emit('live_viewer_count', { count: [...room.members].filter(id => live.sockets.get(id)?.data.role === 'viewer').length });
  const leave = socket => {
    const room = rooms.get(socket.data.sessionId);
    if (!room) return;
    room.members.delete(socket.id); socket.leave(room.id);
    if (room.hostId === socket.id) {
      room.hostId = null; room.publishing = false;
      live.to(room.id).emit('live_host_left');
    } else if (room.hostId) live.to(room.hostId).emit('live_viewer_left', { viewerSocketId: socket.id });
    if (!room.members.size) rooms.delete(room.id);
    else emitCounts(room);
    socket.data.sessionId = null;
  };
  live.use(async (socket, next) => {
    try {
      const token = socket.handshake.auth?.token;
      if (typeof token !== 'string' || token.length > 16384) throw new Error();
      const claims = await verify(token);
      if (!claims.sub || !claims.exp || claims.exp * 1000 <= Date.now()) throw new Error();
      socket.data.token = token; socket.data.claims = claims; next();
    } catch { next(new Error('Vui lòng đăng nhập lại để tham gia livestream.')); }
  });
  live.on('connection', socket => {
    const on = (event, handler) => socket.on(event, async (payload, ack) => {
      try {
        socket.data.rates ||= new Map();
        const rate = socket.data.rates.get(event) || { start: Date.now(), count: 0 };
        if (Date.now() - rate.start >= 1000) { rate.start = Date.now(); rate.count = 0; }
        socket.data.rates.set(event, rate);
        if (++rate.count > (event === 'live_signal' ? 100 : 10)) throw new Error('Thao tác quá nhanh. Vui lòng chờ một chút.');
        if (!payload || typeof payload !== 'object') throw new Error('Dữ liệu không hợp lệ.');
        const result = await handler(payload);
        if (typeof ack === 'function') ack({ ok: true, ...result });
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Thao tác livestream thất bại.';
        if (typeof ack === 'function') ack({ ok: false, message });
        else socket.emit('live_stream_error', { message });
      }
    });
    on('join_live_room', async ({ sessionId, requestedRole }) => {
      if (!idPattern.test(sessionId) || !['host','viewer'].includes(requestedRole) || socket.data.joining) throw new Error('Phòng live không hợp lệ.');
      socket.data.joining = true;
      try {
        const session = await authorize(socket, sessionId, requestedRole);
        if (!socket.connected) throw new Error('Kết nối đã đóng.');
        if (socket.data.sessionId === sessionId && socket.data.role === requestedRole) {
          const room = roomFor(socket);return { hostOnline: Boolean(room.hostId), streamActive: room.publishing };
        }
        const room = rooms.get(sessionId) || { id: sessionId, members: new Set(), hostId: null, publishing: false, session };
        if (requestedRole === 'host' && room.hostId && room.hostId !== socket.id) throw new Error('Phiên đã có người phát sóng. Hãy đóng phòng phát cũ trước.');
        if (requestedRole === 'viewer' && room.members.size >= maxViewers + (room.hostId ? 1 : 0)) throw new Error('Phòng live đã đủ người xem cho cấu hình hiện tại.');
        leave(socket); rooms.set(sessionId, room);
        socket.data.sessionId = sessionId; socket.data.role = requestedRole;
        room.members.add(socket.id); room.session = session; socket.join(sessionId);
        if (requestedRole === 'host') {
          room.hostId = socket.id;socket.to(sessionId).emit('live_host_available');
          for (const id of room.members) if (id !== socket.id) socket.emit('live_viewer_joined', { viewerSocketId: id });
        } else if (room.hostId) live.to(room.hostId).emit('live_viewer_joined', { viewerSocketId: socket.id });
        emitCounts(room);
        if (room.pinnedProductId !== undefined) socket.emit('product_pinned_changed', { productIndex: (room.session.productRefs?.productIds || []).indexOf(room.pinnedProductId), productId: room.pinnedProductId });
        return { hostOnline: Boolean(room.hostId), streamActive: room.publishing };
      } finally { socket.data.joining = false; }
    });
    on('refresh_live_auth', async ({ token }) => {
      if (typeof token !== 'string' || token.length > 16384) throw new Error('Thông tin đăng nhập không hợp lệ.');
      const claims = await verify(token);
      if (claims.sub !== socket.data.claims.sub || !claims.exp || claims.exp * 1000 <= Date.now()) throw new Error('Tài khoản đã thay đổi. Hãy mở lại phòng live.');
      socket.data.token = token; socket.data.claims = claims;
    });
    for (const event of ['live_stream_started','live_stream_stopped']) on(event, async ({ sessionId }) => {
      const room = roomFor(socket);
      if (sessionId !== room.id || socket.id !== room.hostId) throw new Error('Chỉ người phát sóng được thao tác camera.');
      if (event === 'live_stream_started') {
        const session = await authorize(socket, room.id, 'host');
        if (session.status !== 'LIVE') throw new Error('Chờ shop bắt đầu phiên trước khi phát camera.');
      }
      if (!socket.connected || rooms.get(sessionId) !== room) return;
      room.publishing = event === 'live_stream_started'; socket.to(room.id).emit(event);
    });
    on('live_signal', ({ sessionId, targetSocketId, signal }) => {
      const room = roomFor(socket);const target = live.sockets.get(targetSocketId);
      if (sessionId !== room.id || !room.members.has(targetSocketId) || !target || target.data.role === socket.data.role) throw new Error('Không được gửi tín hiệu sang phòng khác.');
      validIdentity(target);
      if (!room.publishing) throw new Error('Camera chưa phát sóng.');
      const description = signal?.description, candidate = signal?.candidate;
      if (description && (description.type !== (socket.data.role === 'host' ? 'offer' : 'answer') || typeof description.sdp !== 'string' || description.sdp.length > 100000)) throw new Error('Tín hiệu video không hợp lệ.');
      if (candidate && (typeof candidate.candidate !== 'string' || candidate.candidate.length > 8192)) throw new Error('Tín hiệu kết nối không hợp lệ.');
      if (!description && !candidate) throw new Error('Thiếu tín hiệu video.');
      live.to(targetSocketId).emit('live_signal', { fromSocketId: socket.id, signal: { ...(description ? { description } : {}), ...(candidate ? { candidate } : {}) } });
    });
    on('send_live_chat', ({ sessionId, text }) => {
      const room = roomFor(socket);
      if (sessionId !== room.id || typeof text !== 'string' || !text.trim() || text.length > 1000) throw new Error('Tin nhắn không hợp lệ.');
      live.to(room.id).emit('new_live_chat', { id: crypto.randomUUID(), sender: socket.data.claims.name || socket.data.claims.preferred_username || 'Người xem', role: socket.data.role === 'host' ? 'HOST' : 'VIEWER', text: text.trim(), time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) });
    });
    on('host_pin_product', ({ sessionId, productIndex, productId }) => {
      const room = roomFor(socket); const ids = room.session.productRefs?.productIds || [];
      if (sessionId !== room.id || room.hostId !== socket.id || !Number.isInteger(productIndex) || productIndex < -1 || productIndex >= ids.length || (productId && ids[productIndex] !== productId)) throw new Error('Sản phẩm ghim không hợp lệ.');
      room.pinnedProductId = productIndex === -1 ? null : ids[productIndex];
      live.to(room.id).emit('product_pinned_changed', { productIndex, productId: room.pinnedProductId });
    });
    on('send_reaction', ({ sessionId, emoji }) => {
      const room = roomFor(socket);
      if (sessionId !== room.id || !emojis.has(emoji)) throw new Error('Biểu cảm không hợp lệ.');
      live.to(room.id).emit('reaction_broadcast', { emoji });
    });
    socket.on('disconnect', () => leave(socket));
  });
  let polling = false;
  const timer = setInterval(async () => {
    if (polling) return;polling = true;
    try {
      for (const room of rooms.values()) {
        for (const id of [...room.members]) {
          const socket = live.sockets.get(id);
          if (socket && socket.data.claims.exp * 1000 <= Date.now()) socket.disconnect(true);
        }
        const member = live.sockets.get(room.hostId) || live.sockets.get(room.members.values().next().value);
        if (!member) continue;
        try {
          const session = await authorize(member, room.id, room.hostId === member.id ? 'host' : 'viewer');
          room.session = session;
        } catch {
          // Fail closed on changed permissions, a closed session, or an unavailable business API.
          live.to(room.id).emit('live_session_ended');
          for (const id of [...room.members]) live.sockets.get(id)?.disconnect(true);
          rooms.delete(room.id);
        }
      }
    } finally { polling = false; }
  }, pollMs);
  timer.unref();
  return { http, io, rooms, close: async () => { clearInterval(timer); await new Promise(resolve => io.close(resolve)); } };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createLiveServer();
  const port = Number(process.env.PORT || 8090), host = process.env.HOST || '127.0.0.1';
  server.http.listen(port, host, () => console.log(`Livestream realtime listening on ${host}:${port}`));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal, async () => { await server.close();process.exit(0); });
}
