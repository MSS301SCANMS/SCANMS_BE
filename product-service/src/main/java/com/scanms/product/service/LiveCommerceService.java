package com.scanms.product.service;

import com.scanms.product.dto.request.*;
import com.scanms.product.dto.response.LiveCommerceResponse;

public interface LiveCommerceService {
    LiveCommerceResponse get(String livestreamId, boolean management);

    LiveCommerceResponse createOffer(String livestreamId, CreateLiveOfferRequest request);

    LiveCommerceResponse stopOffer(String livestreamId, String offerId);

    LiveCommerceResponse createVoucherDrop(String livestreamId, CreateLiveVoucherDropRequest request);

    LiveCommerceResponse cancelVoucherDrop(String livestreamId, String dropId);
}
