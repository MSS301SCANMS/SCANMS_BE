package com.scanms.order.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Map;

public interface CheckoutService {
    record Line(@NotBlank String productId,String variantId,@Min(1) @Max(1000) int quantity,String sourceLivestreamId) {
        public Line(String productId,String variantId,int quantity) { this(productId,variantId,quantity,null); }
    }
    record Input(@NotBlank @Size(max=128) String idempotencyKey,@NotBlank @Size(max=100) String customerName,
        @NotBlank @Size(max=30) String customerPhone,@NotBlank @Size(max=500) String shippingAddress,
        @NotBlank String paymentMethod,@AssertTrue boolean policyAccepted,@Size(max=100) String couponCode,
        @Size(max=500) String orderNotes,@NotEmpty @Size(max=100) List<@Valid Line> items,
        @Min(0) Long expectedShippingFeeVnd) {
        public Input(String key,String name,String phone,String address,String payment,boolean accepted,String coupon,String notes,List<Line> items) {
            this(key,name,phone,address,payment,accepted,coupon,notes,items,null);
        }
    }
    record QuoteInput(@NotEmpty @Size(max=100) List<@Valid Line> items,@Size(max=100) String couponCode) {}

    Map<String,Object> checkout(Input input, Jwt jwt);
    Map<String,Object> quote(QuoteInput input, Jwt jwt);
}
