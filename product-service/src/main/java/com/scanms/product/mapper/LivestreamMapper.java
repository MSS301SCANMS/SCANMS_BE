package com.scanms.product.mapper;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.entity.Livestream;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Map;
import java.util.LinkedHashMap;

@Mapper(componentModel = "spring")
public interface LivestreamMapper {
    Livestream toEntity(CreateLivestreamRequest request);

    @Mapping(target = "participantRefs", qualifiedByName = "publicParticipants")
    @Mapping(target = "likeCount", source = "statistics", qualifiedByName = "reactionCount")
    LivestreamResponse toResponse(Livestream entity);

    @Named("reactionCount")
    default Long reactionCount(Map<String, Object> statistics) {
        return statistics != null && statistics.get("likeCount") instanceof Number value ? Math.max(0, value.longValue()) : 0L;
    }

    @Named("publicParticipants")
    default Map<String, Object> publicParticipants(Map<String, Object> value) {
        if (value == null) return null;
        var result = new LinkedHashMap<String, Object>(value);
        result.remove("voucherDrops");
        return result;
    }
}
