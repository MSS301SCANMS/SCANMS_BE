package com.scanms.order.service.impl;

import com.scanms.order.constant.ReturnRequestStatus;
import com.scanms.order.entity.ReturnRequest;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.service.ShopReturnAccess;
import com.scanms.order.service.ShopReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopReturnServiceImpl implements ShopReturnService {
    private final ReturnRequestRepository returns;
    private final OrderItemRepository items;
    private final SellerOrderRepository sellers;
    private final OrderRepository orders;
    private final ShopReturnAccess access;

    @Override
    public List<View> list(Jwt jwt) {
        var stores = access.stores(jwt);
        if (stores.isEmpty())
            return List.of();
        return returns.findForStores(stores.keySet()).stream().map(row -> view(row, stores)).toList();
    }

    @Override
    public List<View> managerList() {
        Map<String, String> names = new HashMap<>();
        for (var seller : sellers.findAll()) {
            Object name = seller.getTotalsSnapshot() == null ? null : seller.getTotalsSnapshot().get("storeName");
            if (seller.getStoreId() != null)
                names.put(seller.getStoreId(), name instanceof String text && !text.isBlank() ? text : "Gian hàng · " + seller.getStoreId());
        }
        return returns.findAll().stream().sorted(Comparator.comparing(ReturnRequest::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(row -> view(row, names)).toList();
    }

    @Override
    @Transactional
    public Map<String, Object> managerDecide(String id, ManagerDecision input) {
        if (!Set.of(ReturnRequestStatus.APPROVED, ReturnRequestStatus.REJECTED).contains(input.status())
                || input.notes().trim().length() < 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn duyệt hoặc từ chối và nhập lý do ít nhất 20 ký tự.");
        var row = returns.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả."));
        if (row.getStatus() != ReturnRequestStatus.REQUESTED) {
            if (row.getStatus() == input.status() && Objects.equals(row.getDecisionReason(), input.notes().trim()))
                return Map.of("returnRequestId", id, "status", row.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã được xử lý. Vui lòng tải lại danh sách.");
        }
        if (row.getRefundReference() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã có tham chiếu hoàn tiền, cần đối soát trước khi xử lý.");
        row.setStatus(input.status());
        row.setDecisionReason(input.notes().trim());
        row.setResolvedAt(LocalDateTime.now());
        returns.saveAndFlush(row);
        return Map.of("returnRequestId", id, "status", row.getStatus());
    }

    @Override
    @Transactional
    public View respond(String id, ResponseInput input, Jwt jwt) {
        var stores = access.stores(jwt);
        ReturnRequest row = returns.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả."));
        var item = items.findById(row.getOrderItemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var seller = sellers.findById(item.getSellerOrderId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!stores.containsKey(seller.getStoreId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả của gian hàng.");
        String message = input.response() == null ? "" : input.response().trim();
        var images = input.imageUrls() == null ? List.<String>of() : input.imageUrls();
        if (message.isBlank() || message.length() > 5000 || images.size() > 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhập giải trình tối đa 5.000 ký tự và tối đa 10 ảnh.");
        for (String image : images) {
            try {
                URI uri = URI.create(image);
                if (image.length() > 2048 || !Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn ảnh bằng chứng không hợp lệ.");
            }
        }
        var previous = row.getShopResponse();
        if (previous != null && !previous.isEmpty()) {
            if (Objects.equals(previous.get("response"), message) && Objects.equals(previous.get("imageUrls"), images))
                return view(row, stores);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gian hàng đã gửi giải trình. Hãy tải lại để xem thông tin mới nhất.");
        }
        if (row.getStatus() != ReturnRequestStatus.REQUESTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã chuyển trạng thái và không còn nhận giải trình.");
        row.setShopResponse(Map.of("response", message, "imageUrls", List.copyOf(images), "submittedAt", LocalDateTime.now().toString()));
        returns.saveAndFlush(row);
        // A shop statement never decides a return or initiates a refund.
        return view(row, stores);
    }

    @Override
    @Transactional
    public View decide(String id, DecisionInput input, Jwt jwt) {
        var stores = access.stores(jwt);
        ReturnRequest row = returns.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả."));
        var item = items.findById(row.getOrderItemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var seller = sellers.findById(item.getSellerOrderId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!stores.containsKey(seller.getStoreId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đổi trả của gian hàng.");

        if (input.status() != ReturnRequestStatus.APPROVED && input.status() != ReturnRequestStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể chấp thuận hoặc từ chối yêu cầu trả hàng.");
        String reason = input.decisionReason() == null ? "" : input.decisionReason().trim();
        if (reason.length() > 3000 || (input.status() == ReturnRequestStatus.REJECTED && reason.length() < 20))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khi từ chối, vui lòng nhập căn cứ tối thiểu 20 ký tự và tối đa 3.000 ký tự.");

        if (row.getStatus() != ReturnRequestStatus.REQUESTED) {
            if (row.getStatus() == input.status() && Objects.equals(nullIfBlank(row.getDecisionReason()), nullIfBlank(reason)))
                return view(row, stores);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã được xử lý. Vui lòng tải lại danh sách.");
        }
        if (row.getRefundReference() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu đã có tham chiếu hoàn tiền, cần đối soát trước khi xử lý.");

        row.setStatus(input.status());
        row.setDecisionReason(reason.isBlank() ? null : reason);
        row.setResolvedAt(LocalDateTime.now());
        returns.saveAndFlush(row);
        // Shop approval only authorizes the return shipment. Refunds remain gated on receipt and finance processing.
        return view(row, stores);
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private View view(ReturnRequest row, Map<String, String> stores) {
        var item = items.findById(row.getOrderItemId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu thiếu dòng sản phẩm."));
        var seller = sellers.findById(item.getSellerOrderId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Yêu cầu thiếu đơn của gian hàng."));
        if (!stores.containsKey(seller.getStoreId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var order = orders.findById(seller.getOrderId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy đơn hàng gốc."));
        var shipping = order.getShippingSnapshot() == null ? Map.<String, Object>of() : order.getShippingSnapshot();
        var product = item.getProductSnapshot() == null ? Map.<String, Object>of() : item.getProductSnapshot();
        return new View(row.getReturnRequestId(), order.getOrderId(), seller.getSellerOrderId(), item.getOrderItemId(),
                seller.getStoreId(), stores.get(seller.getStoreId()), text(shipping, "name", "customerName"), text(shipping, "phone", "customerPhone"),
                text(product, "productName", "title", "name"), text(product, "imageUrl", "productImageUrl"), row.getQuantity(), row.getRefundAmountVnd(),
                row.getStatus(), row.getReason(), row.getEvidenceRefs(), row.getShopResponse(), row.getDecisionReason(), row.getRequestedAt(), seller.getDeliveredAt(), seller.getReturnDeadline(), row.getResolvedAt());
    }

    private static String text(Map<String, Object> map, String... keys) {
        for (String key : keys)
            if (map.get(key) instanceof String value && !value.isBlank())
                return value;
        return "";
    }
}
