package com.scanms.product.client;

import com.scanms.product.dto.ai.AiDetectionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "ai-service", url = "${clients.ai.url:http://localhost:8000}")
public interface AiServiceClient {

    @PostMapping(value = "/api/v1/ai/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AiDetectionResponse analyze(@RequestPart("image") MultipartFile image);
}
