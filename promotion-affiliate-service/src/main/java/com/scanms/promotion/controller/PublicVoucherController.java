package com.scanms.promotion.controller;

import com.scanms.promotion.constant.VoucherIssuerType;
import com.scanms.promotion.constant.VoucherScopeType;
import jakarta.persistence.EntityManager;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.repository.VoucherRepository;
import com.scanms.promotion.entity.Voucher;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequiredArgsConstructor
public class PublicVoucherController {
    private final VoucherRepository vouchers;
    private final EntityManager em;

    @GetMapping("/api/v1/vouchers/public/platform")
    public ApiResponse<List<Map<String,Object>>> platform(@RequestParam(required=false) String storeId) {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var result = vouchers.findByIssuerType(VoucherIssuerType.PLATFORM).stream()
                .filter(v -> v.getValidFrom()!=null && v.getValidUntil()!=null
                        && !v.getValidFrom().isAfter(now) && v.getValidUntil().isAfter(now))
                .filter(v -> v.getScopeType()==VoucherScopeType.PLATFORM
                        || v.getScopeType()==VoucherScopeType.STORE && Objects.equals(storeId,v.getStoreId()))
                .filter(v -> Set.of("GOODS","SHIPPING").contains(Objects.toString(v.getScope()==null?null:v.getScope().get("benefitType"),"GOODS")))
                .filter(v -> v.getValue()!=null && v.getValue().signum()>0)
                .sorted(Comparator.comparing(Voucher::getValidUntil).thenComparing(Voucher::getCode))
                .limit(100).map(v -> {
                    var row = new LinkedHashMap<String,Object>();
                    row.put("voucherId",v.getVoucherId()); row.put("code",v.getCode());
                    row.put("issuerType",v.getIssuerType()); row.put("scopeType",v.getScopeType());
                    row.put("storeId",v.getStoreId());
                    row.put("benefitType",Objects.toString(v.getScope()==null?null:v.getScope().get("benefitType"),"GOODS"));
                    row.put("discountType",v.getDiscountType()); row.put("value",v.getValue());
                    row.put("cap",v.getCap()); row.put("minimumSubtotal",v.getMinimumSubtotal());
                    row.put("validFrom",v.getValidFrom()); row.put("validUntil",v.getValidUntil());
                    Long remaining = null;
                    if(v.getLimits()!=null && v.getLimits().get("totalUsageLimit") instanceof Number limit) {
                        long used = em.createQuery("select count(r) from VoucherRedemption r where r.voucherId=:id and r.status in :states",Long.class)
                                .setParameter("id",v.getVoucherId())
                                .setParameter("states",List.of(com.scanms.promotion.constant.VoucherRedemptionStatus.RESERVED,com.scanms.promotion.constant.VoucherRedemptionStatus.USED)).getSingleResult();
                        remaining = Math.max(0,limit.longValue()-used);
                    }
                    row.put("remainingUses",remaining);
                    return (Map<String,Object>)row;
                }).filter(row -> row.get("remainingUses")==null || ((Number)row.get("remainingUses")).longValue()>0).toList();
        return ApiResponse.success(result);
    }
    @GetMapping("/api/v1/vouchers/public")
    public ApiResponse<List<Map<String,Object>>> list(@RequestParam String storeId) {
        if(storeId.isBlank()||storeId.length()>255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Store required");
        var now=LocalDateTime.now(ZoneOffset.UTC);
        var result=vouchers.findByStoreId(storeId).stream()
                .filter(v->v.getIssuerType()==VoucherIssuerType.STORE&&v.getValidFrom()!=null&&v.getValidUntil()!=null&&!v.getValidFrom().isAfter(now)&&v.getValidUntil().isAfter(now))
                .limit(50).map(v->{var row=new LinkedHashMap<String,Object>();
                    row.put("voucherId",v.getVoucherId());row.put("code",v.getCode());row.put("storeId",v.getStoreId());
                    row.put("issuerType",v.getIssuerType());row.put("productId",v.getProductId());row.put("livestreamId",v.getLivestreamId());
                    row.put("scopeType",v.getScopeType());row.put("discountType",v.getDiscountType());row.put("value",v.getValue());
                    row.put("cap",v.getCap());row.put("minimumSubtotal",v.getMinimumSubtotal());row.put("validFrom",v.getValidFrom());row.put("validUntil",v.getValidUntil());return (Map<String,Object>)row;}).toList();
        return ApiResponse.success(result);
    }
}
