package com.scanms.product.mapper;

import com.scanms.product.dto.response.*;
import org.mapstruct.Mapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Mapper(componentModel="spring")
public interface LiveCommerceMapper {
    LiveOfferResponse toOffer(Map<String, Object> value);
    LiveVoucherDropResponse toVoucherDrop(Map<String, Object> value);

    default String text(Object value) { return value == null ? null : value.toString(); }
    default Long number(Object value) { return value == null ? null : new BigDecimal(value.toString()).longValueExact(); }
    default BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    default Instant instant(Object value) { return value == null ? null : Instant.parse(value.toString()); }
}
