package com.scanms.product.service;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StoreChatService {
    private final StoreChatConversationRepository conversations;
    private final StoreChatMessageRepository messages;
    private final StoreRepository stores;
    private final ProductAccess access;

    @Transactional(readOnly = true)
    public List<Map<String,Object>> list() {
        String userId = access.userId();
        return conversations.visible(userId).stream().map(c -> conversationDto(c, userId)).toList();
    }

    @Transactional
    public Map<String,Object> open(String storeId) {
        String userId = access.userId();
        Store store = store(storeId);
        if (store.getApprovalStatus() != StoreStatus.APPROVED)
            throw error(HttpStatus.CONFLICT, "Gian hàng chưa hoạt động.");
        if (Objects.equals(store.getOwnerUserId(), userId))
            throw error(HttpStatus.BAD_REQUEST, "Bạn không thể nhắn tin cho chính gian hàng của mình.");
        // Stable ID + DB uniqueness also serializes repeated requests from two browser tabs.
        String id = UUID.nameUUIDFromBytes((storeId + ":" + userId).getBytes(StandardCharsets.UTF_8)).toString();
        String name = "Khách hàng";
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            String claim = auth.getToken().getClaimAsString("name");
            if (claim != null && !claim.isBlank()) name = claim;
        }
        conversations.openIfAbsent(id, storeId, userId, name);
        StoreChatConversation c = conversations.findById(id).orElseThrow();
        return conversationDto(c, userId);
    }

    @Transactional(readOnly = true)
    public List<StoreChatMessage> history(String id, int take, String cursor) {
        String userId = access.userId();
        participant(id, userId, false);
        int limit = Math.max(1, Math.min(100, take));
        List<StoreChatMessage> page;
        if (cursor != null && !cursor.isBlank()) {
            var marker = messages.findById(cursor).filter(m -> id.equals(m.getConversationId()))
                    .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Invalid message cursor"));
            page = new ArrayList<>(messages.before(id, marker.getId(), limit));
        } else page = new ArrayList<>(messages.latest(id, limit, 0));
        Collections.reverse(page);
        return page;
    }

    @Transactional
    public StoreChatMessage send(String id, String text, String messageId) {
        String userId = access.userId();
        StoreChatConversation c = participant(id, userId, true);
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 10000)
            throw error(HttpStatus.BAD_REQUEST, "Tin nhắn cần có nội dung, tối đa 10.000 ký tự.");
        String key;
        try { key = messageId == null ? UUID.randomUUID().toString() : UUID.fromString(messageId).toString(); }
        catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST, "Mã tin nhắn không hợp lệ."); }
        var existing = messages.findById(key);
        if (existing.isPresent()) {
            StoreChatMessage m = existing.get();
            if (!id.equals(m.getConversationId()) || !userId.equals(m.getSenderId()) || !trimmed.equals(m.getMessageText()))
                throw error(HttpStatus.CONFLICT, "Mã tin nhắn đã được sử dụng.");
            return m;
        }
        var message = new StoreChatMessage();
        message.setId(key); message.setConversationId(id); message.setSenderId(userId);
        message.setMessageText(trimmed); message.setCreatedAt(Instant.now()); message.setRead(false);
        c.setUpdatedAt(message.getCreatedAt());
        return messages.save(message);
    }

    @Transactional
    public void read(String id) {
        String userId = access.userId();
        participant(id, userId, false);
        messages.read(id, userId);
    }

    private StoreChatConversation participant(String id, String userId, boolean lock) {
        var c = (lock ? conversations.locked(id) : conversations.findById(id))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc trò chuyện."));
        if (!userId.equals(c.getCustomerId()) && !userId.equals(store(c.getStoreId()).getOwnerUserId()))
            throw error(HttpStatus.FORBIDDEN, "Bạn không có quyền xem cuộc trò chuyện này.");
        return c;
    }

    private Store store(String id) {
        return stores.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy gian hàng."));
    }

    private Map<String,Object> conversationDto(StoreChatConversation c, String userId) {
        var s = store(c.getStoreId());
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("id", c.getId()); row.put("storeId", c.getStoreId()); row.put("customerId", c.getCustomerId());
        row.put("customer", Map.of("id", c.getCustomerId(), "fullName", Objects.toString(c.getCustomerName(), "Khách hàng")));
        row.put("store", PublicCatalogService.toPublicStore(s));
        row.put("lastMessageAt", c.getUpdatedAt()); row.put("createdAt", c.getCreatedAt());
        row.put("chatMessages", messages.latest(c.getId(), 1, 0));
        row.put("_count", Map.of("chatMessages", messages.countByConversationIdAndSenderIdNotAndIsReadFalse(c.getId(), userId)));
        return row;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> messageDto(StoreChatMessage message) {
        var c = conversations.findById(message.getConversationId()).orElseThrow();
        var s = store(c.getStoreId());
        boolean fromCustomer = message.getSenderId().equals(c.getCustomerId());
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("id", message.getId()); row.put("conversationId", message.getConversationId());
        row.put("senderId", message.getSenderId()); row.put("messageText", message.getMessageText());
        row.put("createdAt", message.getCreatedAt()); row.put("isRead", message.isRead());
        row.put("sender", Map.of("id", message.getSenderId(), "fullName", fromCustomer
                ? Objects.toString(c.getCustomerName(), "Khách hàng") : s.getName(), "role", fromCustomer ? "CUSTOMER" : "SHOP_MANAGER"));
        return row;
    }

    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
