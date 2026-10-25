package com.scanms.promotion.service.impl;

import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.CollaborationAccess;
import com.scanms.promotion.service.CollaborationService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {
    private final CollaborationAccess access;
    private final ReferralLinkRepository referrals;
    private final CollaboratorProfileRepository collaborators;

    @Override
    public List<View> list(Jwt jwt, boolean admin) {
        var stores = index(access.stores(jwt, admin), "storeId");
        if (stores.isEmpty()) return List.of();
        var products = index(access.products(jwt), "productId");
        var lives = index(access.livestreams(jwt), "livestreamId");
        var profiles = new HashMap<String, String>();
        collaborators.findAll().forEach(profile -> profiles.put(profile.getCollaboratorId(),
                profile.getApprovalStatus() == null ? "UNKNOWN" : profile.getApprovalStatus().name()));
        List<View> rows = new ArrayList<>();
        for (var link : referrals.findAll()) {
            var product = products.get(link.getProductId());
            if (product == null) continue;
            String storeId = value(product, "storeId");
            if (!stores.containsKey(storeId)) continue;
            var live = lives.get(link.getLivestreamId());
            // Do not expose a livestream that belongs to another store through a malformed referral.
            if (live != null && !storeId.equals(value(live, "storeId"))) live = null;
            String status = link.getStatus() == null ? "INACTIVE" : link.getStatus().name();
            if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) status = "EXPIRED";
            rows.add(new View("referral:" + link.getReferralLinkId(), "REFERRAL", link.getCollaboratorId(), null,
                    profiles.getOrDefault(link.getCollaboratorId(), "UNKNOWN"), storeId, value(stores.get(storeId), "name"),
                    link.getProductId(), value(product, "name"), live == null ? null : link.getLivestreamId(),
                    value(live, "title"), link.getToken(), status, null, date(link.getCreatedAt()), null, date(link.getExpiresAt())));
        }
        for (var live : lives.values()) {
            String storeId = value(live, "storeId");
            if (!stores.containsKey(storeId)) continue;
            Map<?, ?> refs = live.get("collaboratorRefs") instanceof Map<?, ?> map ? map : Map.of();
            if (!(refs.get("collaboratorIds") instanceof Collection<?> ids)) continue;
            Map<?, ?> participants = live.get("participantRefs") instanceof Map<?, ?> map ? map : Map.of();
            for (String collaboratorId : ids.stream().filter(String.class::isInstance).map(String.class::cast).filter(id -> !id.isBlank()).distinct().toList()) {
                rows.add(new View("live:" + value(live, "livestreamId") + ":" + collaboratorId, "LIVESTREAM", collaboratorId,
                        ids.size() == 1 ? value(participants, "creatorName") : null,
                        profiles.getOrDefault(collaboratorId, "UNKNOWN"), storeId, value(stores.get(storeId), "name"), null, null,
                        value(live, "livestreamId"), value(live, "title"), null, value(live, "status"),
                        Optional.ofNullable(value(participants, "inviteStatus")).orElse("PENDING"),
                        value(live, "createdAt"), value(live, "scheduledAt"), null));
            }
        }
        rows.sort(Comparator.comparing(View::createdAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(View::id));
        return rows;
    }

    private static Map<String, Map<?, ?>> index(List<Map<?, ?>> rows, String key) {
        Map<String, Map<?, ?>> index = new LinkedHashMap<>();
        for (var row : rows) { String id = value(row, key); if (id != null) index.put(id, row); }
        return index;
    }
    private static String value(Map<?, ?> row, String key) { return row == null ? null : Objects.toString(row.get(key), null); }
    private static String date(LocalDateTime value) { return value == null ? null : value.toString(); }
}
