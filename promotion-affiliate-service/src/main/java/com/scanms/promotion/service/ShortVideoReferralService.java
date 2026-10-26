package com.scanms.promotion.service;

import jakarta.validation.constraints.*;
import java.util.*;
public interface ShortVideoReferralService {
    record Link(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String productId,
                @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String referralLinkId) {}
    record Click(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String productId,
                 @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String referralLinkId,
                 @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String shortVideoId) {}
    Map<String,String> videoAccess(String storeId,List<Link> links);
    Map<String,String> click(Click input);
    Map<String,String> verifyClick(String id,String productId,String customerId);
}
