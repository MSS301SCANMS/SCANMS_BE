package com.scanms.product.dto.response;

public record ChatAttachmentCardResponse(String type, String attachmentId, String fileName,
                                         String contentType, long sizeBytes, String mediaType) {
}
