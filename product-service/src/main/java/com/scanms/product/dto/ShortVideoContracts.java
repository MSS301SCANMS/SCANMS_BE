package com.scanms.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ShortVideoContracts {
    private ShortVideoContracts() {}
    public record Link(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String productId,
                       @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String referralLinkId) {}
    public record Create(@NotBlank @Size(max=180) String title, @Size(max=2000) String caption,
                         @NotBlank @Size(max=500) String publicId,
                         @NotEmpty @Size(max=5) List<@Valid Link> products) {}
    public record ProductCard(String productId,String referralLinkId,String name,long priceVnd,String imageUrl) {}
    public record View(String id,String collaboratorId,String creatorName,String storeId,String title,String caption,
                       String videoUrl,String posterUrl,double durationSeconds,String status,LocalDateTime createdAt,
                       List<ProductCard> products) {}
    public record Feed(List<View> items,Integer nextPage) {}
}
