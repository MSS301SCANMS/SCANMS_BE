package com.scanms.order.service.impl;

import com.scanms.order.service.*;
import com.scanms.order.client.PromotionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class CheckoutAttributionImpl implements CheckoutAttribution {
    private final PromotionClient promotion;
    @Override @SuppressWarnings("unchecked")
    public Map<String,Object> verify(CheckoutService.Line line,String token,String customer) {
        try {
            Map<String,Object> envelope;
            if(line.referralClickId()!=null)
                envelope=promotion.verifyClick("Bearer "+token,line.referralClickId(),line.productId(),customer);
            else if(line.sourceLivestreamId()!=null)
                envelope=promotion.liveAttribution("Bearer "+token,line.sourceLivestreamId(),line.productId());
            else return Map.of(); // A bare client-supplied referral id has no trusted click time.
            if(envelope==null || !(envelope.get("result") instanceof Map<?,?> result))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Không xác minh được nguồn giới thiệu.");
            if(result.isEmpty() || customer.equals(result.get("userId"))) return Map.of();
            return new LinkedHashMap<>((Map<String,Object>)result);
        } catch(feign.FeignException ex) {
            throw new ResponseStatusException(ex.status()==409?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa xác minh được lượt giới thiệu. Vui lòng thử lại.");
        }
    }
}
