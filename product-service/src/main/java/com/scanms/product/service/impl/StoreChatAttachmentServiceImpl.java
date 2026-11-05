package com.scanms.product.service.impl;

import com.scanms.product.dto.response.ChatAttachmentDownload;
import com.scanms.product.entity.StoreChatAttachment;
import com.scanms.product.entity.StoreChatMessage;
import com.scanms.product.repository.StoreChatAttachmentRepository;
import com.scanms.product.repository.StoreChatConversationRepository;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.service.StoreChatAttachmentService;
import com.scanms.product.service.StoreChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class StoreChatAttachmentServiceImpl implements StoreChatAttachmentService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "pdf", "application/pdf", "txt", "text/plain",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final StoreChatService chat;
    private final ProductAccess access;
    private final StoreChatConversationRepository conversations;
    private final StoreChatAttachmentRepository attachments;

    @Override
    @Transactional
    public StoreChatMessage send(String conversationId, String messageId, MultipartFile file) {
        chat.subscription(conversationId);
        // The conversation lock also serializes retries of the same multipart request.
        conversations.locked(conversationId).orElseThrow();
        String id;
        try {
            id = UUID.fromString(messageId).toString();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Mã tin nhắn không hợp lệ.");
        }
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Chọn tệp có dung lượng từ 1 byte đến 10 MB.");
        }
        String name = fileName(file.getOriginalFilename());
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes((int) MAX_BYTES + 1);
        } catch (IOException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Không đọc được tệp. Vui lòng chọn lại.");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp phải có dung lượng từ 1 byte đến 10 MB.");
        }
        if (type == null || !validContent(extension, bytes)) {
            throw error(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ ảnh JPG, PNG, WebP và tài liệu PDF, DOCX, XLSX, TXT đúng định dạng.");
        }
        String userId = access.userId();
        var existing = attachments.findById(id);
        if (existing.isPresent()) {
            var saved = existing.get();
            if (!conversationId.equals(saved.getConversationId()) || !userId.equals(saved.getUploaderId())
                    || !name.equals(saved.getFileName()) || !Arrays.equals(bytes, saved.getContent())) {
                throw error(HttpStatus.CONFLICT, "Mã tin nhắn đã được sử dụng cho tệp khác.");
            }
        } else {
            var attachment = new StoreChatAttachment();
            attachment.setId(id);
            attachment.setConversationId(conversationId);
            attachment.setUploaderId(userId);
            attachment.setFileName(name);
            attachment.setContentType(type);
            attachment.setSizeBytes(bytes.length);
            attachment.setContent(bytes);
            attachment.setCreatedAt(Instant.now());
            attachments.saveAndFlush(attachment);
        }
        // The file and message commit together; a failed send leaves no orphan upload.
        return chat.send(conversationId, "{\"type\":\"ATTACHMENT\",\"attachmentId\":\"" + id + "\"}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatAttachmentDownload download(String conversationId, String attachmentId) {
        chat.subscription(conversationId);
        var file = attachments.findById(attachmentId)
                .filter(row -> conversationId.equals(row.getConversationId()))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy tệp trong cuộc trò chuyện."));
        return new ChatAttachmentDownload(file.getFileName(), file.getContentType(), file.getContent());
    }

    private String fileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 180 || !name.contains(".")) {
            throw error(HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ hoặc dài quá 180 ký tự.");
        }
        return name;
    }

    private boolean validContent(String extension, byte[] bytes) {
        return switch (extension) {
            case "jpg", "jpeg" -> starts(bytes, 0xff, 0xd8, 0xff);
            case "png" -> starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "webp" -> bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP");
            case "pdf" -> starts(bytes, 0x25, 0x50, 0x44, 0x46, 0x2d);
            case "txt" -> validText(bytes);
            case "docx", "xlsx" -> validOffice(extension, bytes);
            default -> false;
        };
    }

    private boolean validText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            return text.chars().noneMatch(value -> value < 32 && value != 9 && value != 10 && value != 13);
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean validOffice(String extension, byte[] bytes) {
        if (!starts(bytes, 0x50, 0x4b, 0x03, 0x04)) return false;
        String required = extension.equals("docx") ? "word/document.xml" : "xl/workbook.xml";
        boolean contentTypes = false, document = false;
        long expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (int count = 0; count < 1000; count++) {
                var entry = zip.getNextEntry();
                if (entry == null) return contentTypes && document;
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) return false;
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                document |= entry.getName().equals(required);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 50L * 1024 * 1024) return false;
                }
            }
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
