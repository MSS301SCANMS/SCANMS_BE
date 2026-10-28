import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { Server } from 'socket.io';
import { createRemoteJWKSet, jwtVerify } from 'jose';
import { createHmac, randomUUID } from 'node:crypto';

const idPattern = /^[A-Za-z0-9_-]{1,100}$/;
const emojis = new Set(['❤️','👍','😂','😮','😢','😡','🔥','🎁','🎉','💯','⭐']);
const unpack = body => body?.result ?? body?.data ?? body;
const subscriber = socket => ['viewer','moderator'].includes(socket?.data.role);
export function createLiveServer({
  apiUrl = process.env.API_URL || 'http://localhost:8080/api/v1',
  issuer = process.env.KEYCLOAK_ISSUER || 'http://localhost:8180/realms/scanms',
  origins = (process.env.ALLOWED_ORIGINS || 'http://localhost:5173,http://127.0.0.1:5173').split(',').map(value => value.trim()),
  maxViewers = Number(process.env.MAX_VIEWERS_PER_ROOM || 20),
  maxConcurrentJoins = Number(process.env.MAX_CONCURRENT_JOINS || 32),
  pollMs = 10000,
  verifyToken,
  fetcher = fetch,
  metricsSecret = process.env.LIVE_METRICS_SECRET || '',
  reactionFlushMs = 500,
} = {}) {
  if (!Number.isInteger(maxViewers) || maxViewers < 1 || !Number.isInteger(maxConcurrentJoins) || maxConcurrentJoins < 1) throw new Error('Livestream connection limits must be positive integers.');
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
  const reactionStates = new Map();
  let joiningCount = 0;
  const read = async (path, token) => {
    const response = await fetcher(`${apiUrl}${path}`, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(6000) });
    if (!response.ok) { const error=new Error('Không xác minh được quyền hoặc trạng thái phiên live.');error.status=response.status;throw error; }
    return unpack(await response.json());
  };
  const validIdentity = socket => {
    if (!socket.data.claims?.exp || socket.data.claims.exp * 1000 <= Date.now()) {const error=new Error('Phiên đăng nhập đã hết hạn.');error.status=401;throw error;}
  };
  const authorize = async (socket, id, role) => {
    validIdentity(socket);
    const session = await read(`/livestreams/${id}`, socket.data.token);
    const plannedEnd=session.participantRefs?.endsAt || session.endsAt;
    const endTime=plannedEnd ? Date.parse(/(Z|[+-]\d{2}:\d{2})$/.test(plannedEnd) ? plannedEnd : `${plannedEnd}Z`) : NaN;
    if ((session.livestreamId || session.id) !== id || !['LIVE','SCHEDULED'].includes(session.status) || Number.isFinite(endTime) && endTime<=Date.now()) {const error=new Error('Phiên livestream đã kết thúc hoặc hết lịch phát.');error.closed=true;throw error;}
    if (role === 'host' || role === 'moderator') {
      const realmRoles = socket.data.claims.realm_access?.roles || [];
      let authorized = false;
      if (role === 'host' && realmRoles.includes('KOL_CTV')) {
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
      if (!authorized) {const error=new Error('Bạn không có quyền điều khiển phiên livestream này.');error.status=403;throw error;}
    }
    return session;
  };
  const roomFor = socket => {
    validIdentity(socket);
    const room = rooms.get(socket.data.sessionId);
    if (!room || !room.members.has(socket.id)) throw new Error('Bạn chưa tham gia phòng live.');
    return room;
  };
  const pendingViews = new Map();
  const purchaseAccess = async socket => {
    if(socket.data.role!=='viewer')return null;
    if(socket.data.purchaseExpiresAt>Date.now()+30000)return socket.data.purchaseToken;
    const time=String(Math.floor(Date.now()/1000)),id=socket.data.sessionId;
    if(!metricsSecret||!id)return null;
    try {
      const response=await fetcher(`${apiUrl}/livestreams/${encodeURIComponent(id)}/purchase-access`,{method:'POST',
        headers:{Authorization:`Bearer ${socket.data.token}`,'Content-Type':'application/json','X-Live-Metrics-Time':time,'X-Live-Metrics-Signature':createHmac('sha256',metricsSecret).update(`ACCESS\n${id}\n${time}`).digest('hex')},body:'{}',signal:AbortSignal.timeout(6000)});
      const value=response.ok?unpack(await response.json()):null;
      if(typeof value?.token!=='string')return null;
      socket.data.purchaseToken=value.token;socket.data.purchaseExpiresAt=Date.now()+100000;return value.token;
    }catch{return null;}
  };
  const metrics = async (method, id, token, body) => {
    if (!metricsSecret) return false;
    const time = String(Math.floor(Date.now()/1000));
    const canonical = method === 'POST' ? `POST\n${id}\n${body.visitId}` : `PATCH\n${id}\n${body.viewerCount}\n${body.uniqueViewerCount}\n${body.concurrentViewerCount}`;
    const signature = createHmac('sha256',metricsSecret).update(`${canonical}\n${time}`).digest('hex');
    try {
      const response = await fetcher(`${apiUrl}/livestreams/${encodeURIComponent(id)}/${method === 'POST' ? 'views' : 'stats'}`, {
        method, headers: {Authorization:`Bearer ${token}`,'Content-Type':'application/json','X-Live-Metrics-Time':time,'X-Live-Metrics-Signature':signature},
        body:JSON.stringify(body), signal:AbortSignal.timeout(6000),
      });
      return response.ok;
    } catch { return false; }
  };
  let flushingViews;
  const flushViews = () => {
    if (flushingViews) return flushingViews;
    flushingViews = (async () => {
      // Coalesce simultaneous joins and bound outbound Product API work.
      const batch = [...pendingViews].slice(0,64);
      let index = 0;
      await Promise.all(Array.from({length:Math.min(4,batch.length)},async()=>{
        while(index<batch.length){
          const [key,event]=batch[index++];
          if(event.expiresAt*1000<=Date.now()){pendingViews.delete(key);continue;}
          if(await metrics('POST',event.id,event.token,{visitId:event.visitId}))pendingViews.delete(key);
        }
      }));
    })().finally(()=>{flushingViews=null;});
    return flushingViews;
  };
  const audienceCount = room => {
    const members=[...room.members].map(id=>live.sockets.get(id)).filter(Boolean);
    const publisher=live.sockets.get(room.hostId)?.data.claims.sub;
    return new Set(members.filter(socket=>subscriber(socket)&&socket.data.claims.sub!==publisher).map(socket=>socket.data.claims.sub)).size;
  };
  const reactionsFor = (id,session,token) => {
    const saved=Number.isSafeInteger(session.likeCount)&&session.likeCount>=0?session.likeCount:0;
    const state=reactionStates.get(id)||{id,count:saved,persisted:saved,events:new Map(),updatedAt:Date.now()};
    state.count=Math.max(state.count,saved);state.persisted=Math.max(state.persisted,saved);state.token=token;
    reactionStates.set(id,state);return state;
  };
  const persistReactions = state => {
    if(state.saving)return state.saving;
    if(!metricsSecret||state.count<=state.persisted)return Promise.resolve();
    const count=state.count,time=String(Math.floor(Date.now()/1000));
    state.saving=(async()=>{
      try{
        const response=await fetcher(`${apiUrl}/livestreams/${encodeURIComponent(state.id)}/reactions/stats`,{
          method:'PATCH',headers:{Authorization:`Bearer ${state.token}`,'Content-Type':'application/json',
            'X-Live-Metrics-Time':time,'X-Live-Metrics-Signature':createHmac('sha256',metricsSecret).update(`LIKES\n${state.id}\n${count}\n${time}`).digest('hex')},
          body:JSON.stringify({likeCount:count}),signal:AbortSignal.timeout(6000)});
        if(!response.ok)return;
        state.persisted=Math.max(state.persisted,count);
        const saved=unpack(await response.json());
        if(Number.isSafeInteger(saved?.likeCount)&&saved.likeCount>state.count){state.count=saved.likeCount;state.persisted=saved.likeCount;live.to(state.id).emit('live_reaction_count',{likesCount:state.count});}
      }catch{ /* Keep the dirty counter for the next room poll. */ }
    })().finally(()=>{state.saving=null;});
    return state.saving;
  };
  const scheduleReactionSave = state => {
    if(state.timer)return;
    state.timer=setTimeout(()=>{state.timer=null;void persistReactions(state)},reactionFlushMs);state.timer.unref();
  };
  const emitCounts = room => {
    const count=audienceCount(room);
    room.peak=Math.max(room.peak||0,count);
    if(room.peak>(room.persistedPeak||0))room.metricsPending=true;
    if(room.countTimer)return;
    room.countTimer=setTimeout(()=>{
      room.countTimer=null;
      if(rooms.get(room.id)===room)live.to(room.id).emit('live_viewer_count',{count:audienceCount(room)});
    },100);
    room.countTimer.unref();
  };
  const flushPeak = async room => {
    const host=live.sockets.get(room.hostId);
    if(!room.metricsPending||!host||room.session.status!=='LIVE')return;
    const peak=room.peak;
    if(await metrics('PATCH',room.id,host.data.token,{viewerCount:0,uniqueViewerCount:0,concurrentViewerCount:peak}))room.persistedPeak=peak;
    room.metricsPending=room.peak>(room.persistedPeak||0);
  };
  const broadcastPresence = async (socket, state) => {
    if (!metricsSecret || metricsSecret.length < 32 || !socket.data.sessionId) throw new Error('Livestream relay presence is not configured.');
    socket.data.broadcastEpoch ||= Date.now();
    socket.data.broadcastSequence = (socket.data.broadcastSequence || 0) + 1;
    const id = socket.data.sessionId;
    const body = {publisherId:socket.id,epoch:socket.data.broadcastEpoch,sequence:socket.data.broadcastSequence,state};
    const time=String(Math.floor(Date.now()/1000));
    const payload=['BROADCAST',id,body.publisherId,body.epoch,body.sequence,state,time].join('\n');
    const response=await fetcher(`${apiUrl}/livestreams/${encodeURIComponent(id)}/broadcast`, {
      method:'POST',headers:{Authorization:`Bearer ${socket.data.token}`,'Content-Type':'application/json',
        'X-Live-Metrics-Time':time,'X-Live-Metrics-Signature':createHmac('sha256',metricsSecret).update(payload).digest('hex')},
      body:JSON.stringify(body),signal:AbortSignal.timeout(6000),
    });
    if(!response.ok)throw new Error('Unable to confirm broadcast presence.');
    const presence = unpack(await response.json());
    live.to(id).emit('live_broadcast_state', {state:presence?.state || state,reconnectUntil:presence?.reconnectUntil});
    return presence;
  };
  const leave = socket => {
    const room = rooms.get(socket.data.sessionId);
    if (!room) return;
    room.members.delete(socket.id); socket.leave(room.id);
    if (room.hostId === socket.id) {
      if (room.publishing) void broadcastPresence(socket, 'RECONNECTING').catch(() => {});
      room.hostId = null; room.publishing = false;
      live.to(room.id).emit('live_host_left');
    } else if (room.hostId) live.to(room.hostId).emit('live_viewer_left', { viewerSocketId: socket.id });
    if (!room.members.size) {clearTimeout(room.countTimer);rooms.delete(room.id);}
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
        if (typeof ack === 'function') ack({ ok: false, message, ...(error.code?{code:error.code}:{}), ...(error.retryAfterMs?{retryAfterMs:error.retryAfterMs}:{}) });
        else socket.emit('live_stream_error', { message });
      }
    });
    on('join_live_room', async ({ sessionId, requestedRole }) => {
      if (!idPattern.test(sessionId) || !['host','moderator','viewer'].includes(requestedRole) || socket.data.joining) throw new Error('Phòng live không hợp lệ.');
      if(joiningCount>=maxConcurrentJoins){const error=new Error('Nhiều người đang vào phòng. Vui lòng thử kết nối lại sau vài giây.');error.code='ROOM_BUSY';error.retryAfterMs=2000;throw error;}
      socket.data.joining = true;
      joiningCount++;
      try {
        const session = await authorize(socket, sessionId, requestedRole);
        if (!socket.connected) throw new Error('Kết nối đã đóng.');
        if (socket.data.sessionId === sessionId && socket.data.role === requestedRole) {
          const room = roomFor(socket);return { hostOnline: Boolean(room.hostId), streamActive: room.publishing, viewerCount:audienceCount(room), likesCount:reactionsFor(sessionId,session,socket.data.token).count };
        }
        const room = rooms.get(sessionId) || { id: sessionId, members: new Set(), hostId: null, publishing: false, session, messages: [] };
        if (requestedRole === 'host' && room.hostId && room.hostId !== socket.id) throw new Error('Phiên đã có người phát sóng. Hãy đóng phòng phát cũ trước.');
        if (requestedRole !== 'host' && [...room.members].filter(id => subscriber(live.sockets.get(id))).length >= maxViewers) {const error=new Error('Phòng live đã đủ kết nối xem cho cấu hình hiện tại. Vui lòng thử lại sau.');error.code='ROOM_FULL';throw error;}
        leave(socket); rooms.set(sessionId, room);
        socket.data.sessionId = sessionId; socket.data.role = requestedRole;
        room.members.add(socket.id); room.session = session; socket.join(sessionId);
        if (requestedRole === 'host') {
          room.hostId = socket.id;socket.to(sessionId).emit('live_host_available');
          for (const id of room.members) if (subscriber(live.sockets.get(id))) socket.emit('live_viewer_joined', { viewerSocketId: id });
        } else if (room.hostId) live.to(room.hostId).emit('live_viewer_joined', { viewerSocketId: socket.id });
        socket.emit('live_chat_history', { messages: room.messages || [] });
        emitCounts(room);
        if(subscriber(socket)&&session.status==='LIVE'){
          const key=`${sessionId}:${socket.id}`;
          pendingViews.set(key,{id:sessionId,visitId:socket.id,token:socket.data.token,expiresAt:socket.data.claims.exp});
          socket.data.viewQueuedSession=sessionId;void flushViews();
        }
        if (room.pinnedProductId !== undefined) socket.emit('product_pinned_changed', { productIndex: (room.session.productRefs?.productIds || []).indexOf(room.pinnedProductId), productId: room.pinnedProductId });
        const purchaseToken=await purchaseAccess(socket);
        return { hostOnline: Boolean(room.hostId), streamActive: room.publishing, viewerCount:audienceCount(room), likesCount:reactionsFor(sessionId,session,socket.data.token).count, purchaseToken, broadcast:session.participantRefs?.broadcast };
      } finally { socket.data.joining = false;joiningCount--; }
    });
    on('request_live_stream', ({sessionId})=>{
      const room=roomFor(socket);
      if(sessionId!==room.id||!subscriber(socket))throw new Error('Chỉ người nhận video trong phòng được kết nối lại camera.');
      if(Date.now()-(socket.data.lastMediaRequest||0)<1500)throw new Error('Đang thử kết nối camera. Vui lòng chờ một chút.');
      socket.data.lastMediaRequest=Date.now();
      if(room.hostId&&room.publishing)live.to(room.hostId).emit('live_viewer_joined',{viewerSocketId:socket.id,replace:true});
      return {hostOnline:Boolean(room.hostId),streamActive:room.publishing};
    });
    on('refresh_live_auth', async ({ token }) => {
      if (typeof token !== 'string' || token.length > 16384) throw new Error('Thông tin đăng nhập không hợp lệ.');
      const claims = await verify(token);
      if (claims.sub !== socket.data.claims.sub || !claims.exp || claims.exp * 1000 <= Date.now()) throw new Error('Tài khoản đã thay đổi. Hãy mở lại phòng live.');
      socket.data.token = token; socket.data.claims = claims;
      const reactions=reactionStates.get(socket.data.sessionId);if(reactions)reactions.token=token;
      const pending=pendingViews.get(`${socket.data.sessionId}:${socket.id}`);if(pending){pending.token=token;pending.expiresAt=claims.exp;}
      return { purchaseToken: await purchaseAccess(socket) };
    });
    for (const event of ['live_stream_started','live_stream_stopped']) on(event, async ({ sessionId }) => {
      const room = roomFor(socket);
      if (sessionId !== room.id || socket.id !== room.hostId) throw new Error('Chỉ người phát sóng được thao tác camera.');
      if (event === 'live_stream_started') {
        const session = await authorize(socket, room.id, 'host');
        if (session.status !== 'LIVE') throw new Error('Chờ shop bắt đầu phiên trước khi phát camera.');
      }
      if (!socket.connected || rooms.get(sessionId) !== room) return;
      await broadcastPresence(socket, event === 'live_stream_started' ? 'LIVE' : 'RECONNECTING');
      if (!socket.connected || rooms.get(sessionId) !== room) return;
      room.publishing = event === 'live_stream_started'; socket.to(room.id).emit(event);
    });
    on('live_signal', ({ sessionId, targetSocketId, signal }) => {
      const room = roomFor(socket);const target = live.sockets.get(targetSocketId);
      const fromHost=socket.id===room.hostId;
      const validPair=fromHost?subscriber(target):subscriber(socket)&&targetSocketId===room.hostId;
      if (sessionId !== room.id || !room.members.has(targetSocketId) || !target || !validPair) throw new Error('Không được gửi tín hiệu sang phòng khác.');
      validIdentity(target);
      if (!room.publishing) throw new Error('Camera chưa phát sóng.');
      const description = signal?.description, candidate = signal?.candidate;
      if (description && (description.type !== (fromHost ? 'offer' : 'answer') || typeof description.sdp !== 'string' || description.sdp.length > 100000)) throw new Error('Tín hiệu video không hợp lệ.');
      if (candidate && (typeof candidate.candidate !== 'string' || candidate.candidate.length > 8192)) throw new Error('Tín hiệu kết nối không hợp lệ.');
      if (!description && !candidate) throw new Error('Thiếu tín hiệu video.');
      live.to(targetSocketId).emit('live_signal', { fromSocketId: socket.id, signal: { ...(description ? { description } : {}), ...(candidate ? { candidate } : {}) } });
    });
    on('send_live_chat', ({ sessionId, text, clientMessageId }) => {
      const room = roomFor(socket);
      if (sessionId !== room.id || typeof text !== 'string' || !text.trim() || text.length > 1000) throw new Error('Tin nhắn không hợp lệ.');
      if (clientMessageId != null && !idPattern.test(clientMessageId)) throw new Error('Mã tin nhắn không hợp lệ.');
      const previous = room.messages?.find(message => message.senderId === socket.data.claims.sub && message.clientMessageId === clientMessageId && clientMessageId);
      if (previous) return { message: previous };
      const message = { id: randomUUID(), clientMessageId, senderId: socket.data.claims.sub, sender: socket.data.claims.name || socket.data.claims.preferred_username || 'Người xem', role: socket.data.role === 'host' ? 'HOST' : socket.data.role === 'moderator' ? 'SHOP' : 'VIEWER', text: text.trim(), createdAt: new Date().toISOString(), time: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) };
      room.messages = [...(room.messages || []), message].slice(-100);
      live.to(room.id).emit('new_live_chat', message);
      return { message };
    });
    on('host_pin_product', async ({ sessionId, productIndex, productId }) => {
      const room = roomFor(socket); const ids = room.session.productRefs?.productIds || [];
      if (sessionId !== room.id || (room.hostId !== socket.id && socket.data.role !== 'moderator') || !Number.isInteger(productIndex) || productIndex < -1 || productIndex >= ids.length || (productId && ids[productIndex] !== productId)) throw new Error('Sản phẩm ghim không hợp lệ.');
      const pinned=productIndex === -1 ? null : ids[productIndex];
      const response=await fetcher(`${apiUrl}/livestreams/${encodeURIComponent(sessionId)}/pin`,{method:'POST',headers:{Authorization:`Bearer ${socket.data.token}`,'Content-Type':'application/json'},body:JSON.stringify({productId:pinned}),signal:AbortSignal.timeout(6000)});
      if(!response.ok){
        const detail=await response.json().catch(()=>null);
        throw new Error(response.status===404?'Chức năng ghim chưa sẵn sàng ở máy chủ. Cần cập nhật dịch vụ rồi thử lại.':detail?.message||'Chưa lưu được sản phẩm ghim. Hãy thử lại.');
      }
      room.pinnedProductId = pinned;
      room.session.productRefs={...room.session.productRefs,pinnedProductId:pinned};
      live.to(room.id).emit('product_pinned_changed', { productIndex, productId: room.pinnedProductId });
    });
    on('send_reaction', ({ sessionId, emoji, clientReactionId }) => {
      const room = roomFor(socket);
      if (sessionId !== room.id || !emojis.has(emoji)||room.session.status!=='LIVE') throw new Error('Biểu cảm hoặc trạng thái phiên không hợp lệ.');
      if(clientReactionId!=null&&!idPattern.test(clientReactionId))throw new Error('Mã biểu cảm không hợp lệ.');
      const state=reactionsFor(room.id,room.session,socket.data.token),now=Date.now();
      const key=`${socket.data.claims.sub}:${clientReactionId||randomUUID()}`;
      const previous=state.events.get(key);if(previous)return {reaction:previous};
      if(emoji==='❤️'&&state.count<Number.MAX_SAFE_INTEGER)state.count++;
      const reaction={id:randomUUID(),emoji,likesCount:state.count,fromSocketId:socket.id};
      state.events.set(key,reaction);state.updatedAt=now;
      while(state.events.size>2000)state.events.delete(state.events.keys().next().value);
      if(emoji==='❤️')scheduleReactionSave(state);
      live.to(room.id).emit('reaction_broadcast', reaction);
      return {reaction};
    });
    socket.on('disconnect', () => leave(socket));
  });
  let polling = false;
  const timer = setInterval(async () => {
    if (polling) return;polling = true;
    try {
      await flushViews();
      for (const room of rooms.values()) {
        for (const id of [...room.members]) {
          const socket = live.sockets.get(id);
          if (socket && socket.data.claims.exp * 1000 <= Date.now()) socket.disconnect(true);
        }
        const member = live.sockets.get(room.hostId) || live.sockets.get(room.members.values().next().value);
        if (!member) continue;
        try {
          const session = await authorize(member, room.id, member.data.role);
          room.session = session;
          const publisher = live.sockets.get(room.hostId);
          if (publisher && room.publishing) await broadcastPresence(publisher, 'LIVE');
          if(room.serviceUnavailable){room.serviceUnavailable=false;live.to(room.id).emit('live_service_ready');}
          if(Object.hasOwn(session.productRefs || {},'pinnedProductId') && room.pinnedProductId!==session.productRefs.pinnedProductId){
            room.pinnedProductId=session.productRefs.pinnedProductId;
            live.to(room.id).emit('product_pinned_changed',{productId:room.pinnedProductId,productIndex:(session.productRefs.productIds || []).indexOf(room.pinnedProductId)});
          }
          if(session.status==='LIVE')for(const id of room.members){
            const viewer=live.sockets.get(id);
            if(subscriber(viewer)&&viewer.data.viewQueuedSession!==room.id){
              pendingViews.set(`${room.id}:${id}`,{id:room.id,visitId:id,token:viewer.data.token,expiresAt:viewer.data.claims.exp});viewer.data.viewQueuedSession=room.id;
            }
          }
          await flushPeak(room);
        } catch (error) {
          if(error.closed){
            live.to(room.id).emit('live_session_ended');
            for (const id of [...room.members]) live.sockets.get(id)?.disconnect(true);
            rooms.delete(room.id);
          }else if([401,403,404].includes(error.status)){member.disconnect(true);}
          else {room.serviceUnavailable=true;live.to(room.id).emit('live_stream_error',{message:'Đang kết nối lại dịch vụ phiên live. Nội dung chat chưa gửi vẫn được giữ lại.'});}
        }
      }
      for(const [id,state]of reactionStates){
        await persistReactions(state);
        if(!rooms.has(id)&&Date.now()-state.updatedAt>600000){clearTimeout(state.timer);reactionStates.delete(id);}
      }
    } finally { polling = false; }
  }, pollMs);
  timer.unref();
  return { http, io, rooms, close: async () => { clearInterval(timer);for(const room of rooms.values())clearTimeout(room.countTimer);for(const state of reactionStates.values())clearTimeout(state.timer);await flushViews();await Promise.allSettled([...rooms.values()].map(flushPeak));await Promise.allSettled([...reactionStates.values()].map(persistReactions));await new Promise(resolve => io.close(resolve)); } };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createLiveServer();
  const port = Number(process.env.PORT || 8090), host = process.env.HOST || '127.0.0.1';
  server.http.listen(port, host, () => console.log(`Livestream realtime listening on ${host}:${port}`));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal, async () => { await server.close();process.exit(0); });
}
