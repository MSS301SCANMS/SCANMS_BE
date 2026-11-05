package com.scanms.product.service;

import com.scanms.product.dto.response.ChatAttachmentDownload;
import com.scanms.product.entity.StoreChatMessage;
import org.springframework.web.multipart.MultipartFile;

public interface StoreChatAttachmentService {
    StoreChatMessage send(String conversationId, String messageId, MultipartFile file);

    ChatAttachmentDownload download(String conversationId, String attachmentId);
}
