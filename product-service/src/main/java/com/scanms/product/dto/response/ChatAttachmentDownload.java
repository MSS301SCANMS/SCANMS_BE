package com.scanms.product.dto.response;

public record ChatAttachmentDownload(String fileName, String contentType, byte[] content) {
}
