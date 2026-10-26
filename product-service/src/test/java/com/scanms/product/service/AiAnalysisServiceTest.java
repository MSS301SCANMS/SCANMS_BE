package com.scanms.product.service;

import com.scanms.product.client.AiServiceClient;
import com.scanms.product.dto.ai.*;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.service.impl.AiAnalysisServiceImpl;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiAnalysisServiceTest {

    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AiAnalysisService service = new AiAnalysisServiceImpl(client);

    @Test
    void analyzeReturnsDetectionPayloadWithGarmentHints() {
        var response = new AiDetectionResponse(
                List.of(new AiDetectionItem(1, "short_sleeve_top", 0.91, null)),
                new AiTopPrediction("short_sleeve_top", 0.91),
                List.of(new AiDominantColor("white", 0.6)),
                new AiStylePrediction("CASUAL", 0.8),
                false, true, 12.5);
        when(client.analyze(any())).thenReturn(response);

        var result = service.analyze(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertEquals("short_sleeve_top", result.topPrediction().className());
        assertEquals("TOP", result.garmentType());
        assertEquals("Áo tay ngắn", result.displayLabel());
        assertEquals(ClothingGarmentMapper.SUGGESTED_CATEGORY, result.suggestedCategory());
        assertTrue(result.clothingDetected());
        verify(client).analyze(any());
    }

    @Test
    void emptyImageRejected() {
        var ex = assertThrows(AppException.class,
                () -> service.analyze(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0])));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        verify(client, never()).analyze(any());
    }

    @Test
    void unavailableAiMapsToDomainError() {
        Request request = Request.create(Request.HttpMethod.POST, "/api/v1/ai/analyze",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(client.analyze(any())).thenThrow(new FeignException.ServiceUnavailable("down", request, null, Collections.emptyMap()));

        var ex = assertThrows(AppException.class,
                () -> service.analyze(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1})));
        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, ex.getErrorCode());
    }
}
