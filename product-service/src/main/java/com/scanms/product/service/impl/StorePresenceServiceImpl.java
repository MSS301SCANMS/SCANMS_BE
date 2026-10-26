package com.scanms.product.service.impl;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.dto.response.StorePresenceResponse;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.service.StorePresenceService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StorePresenceServiceImpl implements StorePresenceService {
    private final StoreRepository stores;
    private final ProductAccess access;

    @Override
    @Transactional
    public List<StorePresenceResponse> heartbeat() {
        String owner = access.userId();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        stores.touchPresence(owner, StoreStatus.APPROVED, now);
        var owned = stores.findByOwnerUserId(owner);
        Instant checkedAt = Instant.now();
        var result = owned.stream()
                .filter(s -> s.getApprovalStatus() == StoreStatus.APPROVED)
                .map(s -> view(s, checkedAt))
                .toList();
        if (result.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bạn chưa có gian hàng đã được duyệt để cập nhật hoạt động.");
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public StorePresenceResponse publicPresence(String storeId) {
        var store = stores.findById(storeId)
                .filter(s -> s.getApprovalStatus() == StoreStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy gian hàng đang hoạt động."));
        return view(store, Instant.now());
    }

    private StorePresenceResponse view(Store store, Instant now) {
        Instant last = store.getLastActiveAt();
        boolean online = last != null && !last.isAfter(now) && last.isAfter(now.minusSeconds(75));
        return new StorePresenceResponse(store.getStoreId(), online, last, now);
    }
}
