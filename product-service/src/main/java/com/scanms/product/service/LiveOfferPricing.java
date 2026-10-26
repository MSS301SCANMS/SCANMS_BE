package com.scanms.product.service;

import com.scanms.product.entity.Livestream;

import java.util.Map;

public interface LiveOfferPricing {
    Map<String, Object> apply(Livestream live, String variantId, int quantity, boolean reserve,
                              String purchaseToken, String buyerIdentitySubject);

    void release(Map<String, Object> reservationLine);
}
