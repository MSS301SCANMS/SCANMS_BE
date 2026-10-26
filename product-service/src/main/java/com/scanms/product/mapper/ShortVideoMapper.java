package com.scanms.product.mapper;

import com.scanms.product.entity.ShortVideo;
import com.scanms.product.dto.ShortVideoContracts.*;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel="spring")
public interface ShortVideoMapper {
    View view(ShortVideo video, List<ProductCard> products);
}
