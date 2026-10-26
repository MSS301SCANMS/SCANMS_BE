package com.scanms.product.service;

import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.dto.request.UpdateLivestreamRequest;
import com.scanms.product.entity.Livestream;
import com.scanms.product.mapper.LivestreamMapper;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.service.impl.LivestreamServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mapstruct.factory.Mappers;

class LivestreamReferenceUpdateTest {
    private final LivestreamRepository livestreams = mock(LivestreamRepository.class);
    private final ProductAccess access = mock(ProductAccess.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final LivestreamServiceImpl service = new LivestreamServiceImpl(livestreams,
            Mappers.getMapper(LivestreamMapper.class), access, products,
            mock(com.scanms.product.client.LivestreamAffiliateClient.class),
            new LivestreamSchedulePolicy(), mock(LivestreamLifecycleService.class));

    @Test void sellerCanUpdateParticipantsAndApprovedCollaboratorReferences() {
        var live = Livestream.builder().livestreamId("live-1").storeId("store-1")
                .status(LivestreamStatus.SCHEDULED).build();
        when(livestreams.lockById("live-1")).thenReturn(Optional.of(live));
        when(livestreams.save(any())).thenAnswer(call -> call.getArgument(0));
        var participants = Map.<String, Object>of("hostIds", List.of("host-1"));
        var collaborators = Map.<String, Object>of("collaboratorIds", List.of());
        var request = new UpdateLivestreamRequest("New title", "New description", LivestreamStatus.ENDED,
                null, null, null, participants, collaborators, null, null, null);

        var result = service.update("live-1", request);

        assertEquals(participants.get("hostIds"), result.participantRefs().get("hostIds"));
        assertEquals("NOT_REQUIRED", result.participantRefs().get("inviteStatus"));
        assertEquals(collaborators, result.collaboratorRefs());
        assertEquals("New description", result.description());
        assertEquals(LivestreamStatus.SCHEDULED, result.status());
        verify(access).requireApprovedStore("store-1");
    }
}
