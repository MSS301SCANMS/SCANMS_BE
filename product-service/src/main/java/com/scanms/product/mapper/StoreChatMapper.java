package com.scanms.product.mapper;

import com.scanms.product.dto.response.ChatProductCardResponse;
import com.scanms.product.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StoreChatMapper {
    @Mapping(target = "type", constant = "PRODUCT_INQUIRY")
    @Mapping(target = "productId", source = "product.productId")
    @Mapping(target = "productTitle", source = "product.name")
    @Mapping(target = "productPrice", source = "product.priceVnd")
    @Mapping(target = "productImage", source = "imageUrl")
    @Mapping(target = "message", constant = "Trao đổi về sản phẩm")
    ChatProductCardResponse productCard(Product product, String imageUrl);
}
