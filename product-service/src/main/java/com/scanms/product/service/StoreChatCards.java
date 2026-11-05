package com.scanms.product.service;

import com.scanms.product.client.ChatCollaborationClient;
import com.scanms.product.entity.ProductImage;
import com.scanms.product.mapper.StoreChatMapper;
import com.scanms.product.repository.ProductImageRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreChatAttachmentRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StoreChatCards {
    private final ChatCollaborationClient collaboration;
    private final ProductAccess access;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final StoreChatMapper mapper;
    private final StoreChatAttachmentRepository attachments;
    private final JsonMapper json = JsonMapper.builder().build();

    public String validate(String text, String storeId, String customerId) {
        if (!text.startsWith("{")) return text;
        Map<?, ?> card;
        try {
            card = json.readValue(text, Map.class);
        } catch (RuntimeException ex) {
            return text;
        }
        String type = String.valueOf(card.get("type"));
        if ("PRODUCT_INQUIRY".equals(type)) {
            var product = products.findById(String.valueOf(card.get("productId")))
                    .filter(p -> storeId.equals(p.getStoreId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm không thuộc cuộc trò chuyện."));
            String image = images.findByProductId(product.getProductId()).stream()
                    .sorted(Comparator.comparing((ProductImage i) -> !i.isPrimaryImage())
                            .thenComparing(i -> i.getSortOrder() == null ? Integer.MAX_VALUE : i.getSortOrder()))
                    .map(ProductImage::getImageUrl).findFirst().orElse(null);
            return json.writeValueAsString(mapper.productCard(product, image));
        }
        if (!Set.of("COMMISSION_DEAL", "SAMPLE_REQUEST").contains(type)) return text;
        Map<?, ?> resource = resource(type, String.valueOf(card.get("resourceId")));
        if (!storeId.equals(resource.get("storeId")) || !customerId.equals(resource.get("customerId")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đề xuất không thuộc cuộc trò chuyện.");
        return json.writeValueAsString(Map.of("type", type, "resourceId", card.get("resourceId")));
    }

    public String validateAttachment(String text, String conversationId, String userId, String messageId) {
        if (!text.startsWith("{")) return text;
        Map<?, ?> card;
        try {
            card = json.readValue(text, Map.class);
        } catch (RuntimeException exception) {
            return text;
        }
        if (!"ATTACHMENT".equals(card.get("type"))) return text;
        var file = attachments.findById(String.valueOf(card.get("attachmentId")))
                .filter(row -> conversationId.equals(row.getConversationId()) && userId.equals(row.getUploaderId())
                        && row.getId().equals(messageId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tệp không thuộc tin nhắn của bạn trong cuộc trò chuyện."));
        return json.writeValueAsString(mapper.attachmentCard(file));
    }

    public Map<?, ?> resource(String type, String id) {
        try {
            var response = collaboration.resource("Bearer " + access.bearerToken(), type, id);
            if (response != null && response.get("result") instanceof Map<?, ?> row) return row;
        } catch (FeignException ex) {
            throw failure(ex);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không xác minh được đề xuất.");
    }

    public Map<?, ?> requirePartner(String storeId, String customerId) {
        try {
            var response = collaboration.partner("Bearer " + access.bearerToken(), storeId, customerId);
            if (response != null && response.get("result") instanceof Map<?, ?> row
                    && customerId.equals(row.get("customerId")) && storeId.equals(row.get("storeId"))) return row;
        } catch (FeignException ex) {
            throw failure(ex);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa có quan hệ hợp tác với KOL.");
    }

    public boolean sameReference(String incoming, String saved) {
        if (incoming.equals(saved)) return true;
        try {
            Map<?, ?> left = json.readValue(incoming, Map.class);
            Map<?, ?> right = json.readValue(saved, Map.class);
            String type = String.valueOf(left.get("type"));
            String key = "PRODUCT_INQUIRY".equals(type) ? "productId" : "resourceId";
            return Set.of("PRODUCT_INQUIRY", "COMMISSION_DEAL", "SAMPLE_REQUEST").contains(type)
                    && type.equals(right.get("type")) && left.get(key) != null && left.get(key).equals(right.get(key));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private ResponseStatusException failure(FeignException ex) {
        return new ResponseStatusException(ex.status() == 403 || ex.status() == 404 ? HttpStatus.FORBIDDEN : HttpStatus.SERVICE_UNAVAILABLE,
                "Không xác minh được quyền trao đổi hợp tác.");
    }
}
