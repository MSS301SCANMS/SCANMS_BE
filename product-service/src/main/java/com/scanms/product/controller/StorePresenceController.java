package com.scanms.product.controller;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.dto.ApiResponse;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.ProductAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/v1/stores") @RequiredArgsConstructor
public class StorePresenceController {
    private final StoreRepository stores;
    private final ProductAccess access;
    public record Presence(String storeId,boolean isOnline,Instant lastActiveAt,Instant checkedAt) {}

    @PostMapping("/my-store/presence") @Transactional
    public ApiResponse<List<Presence>> heartbeat() {
        String owner=access.userId();Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        stores.touchPresence(owner,StoreStatus.APPROVED,now);
        var owned=stores.findByOwnerUserId(owner);Instant checkedAt=Instant.now();
        var result=owned.stream().filter(s->s.getApprovalStatus()==StoreStatus.APPROVED).map(s->view(s,checkedAt)).toList();
        if(result.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Bạn chưa có gian hàng đã được duyệt để cập nhật hoạt động.");
        return ApiResponse.success(result);
    }

    @GetMapping("/public/id/{storeId}/presence") @Transactional(readOnly=true)
    public ApiResponse<Presence> presence(@PathVariable String storeId) {
        var store=stores.findById(storeId).filter(s->s.getApprovalStatus()==StoreStatus.APPROVED)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy gian hàng đang hoạt động."));
        return ApiResponse.success(view(store,Instant.now()));
    }
    private Presence view(Store store,Instant now) {
        Instant last=store.getLastActiveAt();
        boolean online=last!=null && !last.isAfter(now) && last.isAfter(now.minusSeconds(75));
        return new Presence(store.getStoreId(),online,last,now);
    }
}
