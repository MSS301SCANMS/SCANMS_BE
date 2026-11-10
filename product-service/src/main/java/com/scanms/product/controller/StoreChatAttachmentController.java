package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.service.StoreChatAttachmentService;
import com.scanms.product.service.StoreChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat/conversations/{conversationId}/attachments")
@RequiredArgsConstructor
public class StoreChatAttachmentController {
    private final StoreChatAttachmentService attachments;
    private final StoreChatService chat;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> send(@PathVariable String conversationId,
                                               @RequestParam String messageId, @RequestParam MultipartFile file) {
        return ApiResponse.created(chat.messageDto(attachments.send(conversationId, messageId, file)));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable String conversationId, @PathVariable String attachmentId) {
        var file = attachments.download(conversationId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
