package com.scanms.promotion.controller;

import com.scanms.promotion.constant.VoucherIssuerType;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController @RequiredArgsConstructor
public class PublicVoucherController {
    private final VoucherRepository vouchers;
    @GetMapping("/api/v1/vouchers/public")
    public ApiResponse<List<Map<String,Object>>> list(@RequestParam String storeId) {
        if(storeId.isBlank()||storeId.length()>255) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Store required");
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
