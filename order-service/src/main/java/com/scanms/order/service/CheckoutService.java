package com.scanms.order.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

public interface CheckoutService {
    record ShippingDestination(@NotBlank @Size(max = 150) String province, @NotBlank @Size(max = 150) String district,
                               @NotBlank @Size(max = 150) String ward,
                               @NotBlank @Size(min = 5, max = 500) String detail) {
    }

    record Line(@NotBlank String productId, String variantId, @Min(1) @Max(1000) int quantity,
                @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String sourceLivestreamId,
                @Size(max = 100) String referralLinkId,
                @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String referralClickId,
                @Size(max = 3000) String livePurchaseToken) {
        public Line(String productId, String variantId, int quantity, String liveId, String referralLinkId, String referralClickId) {
            this(productId, variantId, quantity, liveId, referralLinkId, referralClickId, null);
        }

        public Line(String productId, String variantId, int quantity, String liveId, String referralLinkId) {
            this(productId, variantId, quantity, liveId, referralLinkId, null, null);
        }

        public Line(String productId, String variantId, int quantity, String liveId) {
            this(productId, variantId, quantity, liveId, null, null, null);
        }

        public Line(String productId, String variantId, int quantity) {
            this(productId, variantId, quantity, null, null, null, null);
        }
    }

    record Input(@NotBlank @Size(max = 128) String idempotencyKey, @NotBlank @Size(max = 100) String customerName,
                 @NotBlank @Size(max = 30) String customerPhone, @NotBlank @Size(max = 500) String shippingAddress,
                 @NotBlank String paymentMethod, @AssertTrue boolean policyAccepted, @Size(max = 100) String couponCode,
                 @Size(max = 500) String orderNotes, @NotEmpty @Size(max = 100) List<@Valid Line> items,
                 @Min(0) Long expectedShippingFeeVnd,
                 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Size(max = 100) String shippingCouponCode,
                 @Min(0) Long expectedPayableVnd,
                 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Valid ShippingDestination shippingDestination) {
        public Input(String key, String name, String phone, String address, String payment, boolean accepted, String coupon, String notes, List<Line> items, Long shippingFee, String shippingCoupon, ShippingDestination destination) {
            this(key, name, phone, address, payment, accepted, coupon, notes, items, shippingFee, shippingCoupon, null, destination);
        }

        public Input(String key, String name, String phone, String address, String payment, boolean accepted, String coupon, String notes, List<Line> items, Long shippingFee, String shippingCoupon) {
            this(key, name, phone, address, payment, accepted, coupon, notes, items, shippingFee, shippingCoupon, null, null);
        }

        public Input(String key, String name, String phone, String address, String payment, boolean accepted, String coupon, String notes, List<Line> items, Long shippingFee) {
            this(key, name, phone, address, payment, accepted, coupon, notes, items, shippingFee, null, null, null);
        }

        public Input(String key, String name, String phone, String address, String payment, boolean accepted, String coupon, String notes, List<Line> items) {
            this(key, name, phone, address, payment, accepted, coupon, notes, items, null, null, null, null);
        }
    }

    record QuoteInput(@NotEmpty @Size(max = 100) List<@Valid Line> items, @Size(max = 100) String couponCode,
                      @Size(max = 100) String shippingCouponCode,
                      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Valid ShippingDestination shippingDestination) {
        public QuoteInput(List<Line> items, String code, String shippingCode) {
            this(items, code, shippingCode, null);
        }

        public QuoteInput(List<Line> items, String code) {
            this(items, code, null, null);
        }
    }

    Map<String, Object> checkout(Input input, Jwt jwt);

    Map<String, Object> quote(QuoteInput input, Jwt jwt);
}
