const unwrap = body => body?.result ?? body?.data ?? body;
const idPattern = /^[A-Za-z0-9_-]{1,100}$/;

/** Persistent chat shares the relay transport; all authorization and writes stay in Spring. */
export function registerStoreChat(io, { apiUrl, verify, fetcher }) {
  const chat = io.of('/chat');
  async function request(socket, path, method = 'GET', body) {
    if (!socket.data.chatClaims?.exp || socket.data.chatClaims.exp * 1000 <= Date.now()) throw new Error('Phiên đăng nhập hết hạn.');
    const response = await fetcher(`${apiUrl}/chat/conversations${path}`, {
      method, headers: { Authorization: `Bearer ${socket.data.chatToken}`, 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) throw new Error(response.status === 403 || response.status === 404 ? 'Bạn không có quyền truy cập cuộc trò chuyện.' : 'Chưa kết nối được dịch vụ chat.');
    return unwrap(await response.json());
  }
  chat.use(async (socket, next) => {
    try {
      const token = socket.handshake.auth?.token;
      if (typeof token !== 'string') throw new Error('Vui lòng đăng nhập.');
      socket.data.chatClaims = await verify(token);
      socket.data.chatToken = token;
      const identity = await request(socket, '/identity');
      if (!identity?.userId) throw new Error('Không xác minh được tài khoản.');
      socket.data.chatUserId = identity.userId;
      next();
    } catch (error) { next(new Error(error.message)); }
  });
  chat.on('connection', socket => {
    const expiry = setTimeout(() => socket.disconnect(true), Math.min(2147483647, Math.max(1, socket.data.chatClaims.exp * 1000 - Date.now())));
    expiry.unref();
    socket.once('disconnect', () => clearTimeout(expiry));
    socket.join(`user:${socket.data.chatUserId}`);
    socket.emit('connected', { userId: socket.data.chatUserId });
    function on(name, handler) {
      socket.on(name, async (input = {}, ack) => {
        try {
          if (!idPattern.test(input.conversationId || '')) throw new Error('Mã cuộc trò chuyện không hợp lệ.');
          const result = await handler(input);
          if (typeof ack === 'function') ack({ ok: true, ...result });
        } catch (error) {
          if (typeof ack === 'function') ack({ ok: false, message: error.message });
          else socket.emit('error', { message: error.message });
        }
      });
    }
    on('join_conversation', async ({ conversationId }) => {
      await request(socket, `/${conversationId}/subscription`);
      await socket.join(`conversation:${conversationId}`);
      return {};
    });
    on('leave_conversation', async ({ conversationId }) => {
      await socket.leave(`conversation:${conversationId}`);
      return {};
    });
    on('send_message', async ({ conversationId, messageText, messageId }) => {
      if (typeof messageText !== 'string' || !messageText.trim() || messageText.length > 10000 || typeof messageId !== 'string')
        throw new Error('Tin nhắn không hợp lệ.');
      const participants = await request(socket, `/${conversationId}/subscription`);
      const message = await request(socket, `/${conversationId}/messages`, 'POST', { messageText, messageId });
      // Spring has committed successfully before clients receive the event/ack.
      for (const id of participants.participantIds) chat.to(`user:${id}`).emit('new_message', message);
      return { message };
    });
    on('mark_read', async ({ conversationId }) => {
      const participants = await request(socket, `/${conversationId}/subscription`);
      await request(socket, `/${conversationId}/read`, 'PATCH');
      for (const id of participants.participantIds) chat.to(`user:${id}`).emit('messages_read', { conversationId, readerId: socket.data.chatUserId });
      return {};
    });
  });
}
