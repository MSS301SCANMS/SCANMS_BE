package com.scanms.product.controller;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.StoreChatAttachmentRepository;
import com.scanms.product.repository.StoreChatConversationRepository;
import com.scanms.product.repository.StoreChatMessageRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.service.StoreChatAttachmentService;
import com.scanms.product.service.StoreChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:chat-attachment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.catalog.seed-enabled=false", "scanms.catalog.cache-enabled=false", "logging.level.root=WARN"})
class StoreChatAttachmentIntegrationTest {
    @Autowired StoreChatAttachmentService attachments;
    @Autowired StoreChatAttachmentRepository files;
    @Autowired StoreChatService chat;
    @Autowired StoreRepository stores;
    @Autowired StoreChatMessageRepository messages;
    @Autowired StoreChatConversationRepository conversations;
    @Autowired WebApplicationContext context;
    @MockitoBean ProductAccess access;
    @MockitoBean JwtDecoder decoder;
    String actor, conversationId;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        files.deleteAll(); messages.deleteAll(); conversations.deleteAll(); stores.deleteAll();
        actor = "buyer";
        when(access.userId()).thenAnswer(call -> actor);
        var store = stores.saveAndFlush(Store.builder().name("Attachment Shop").ownerUserId("owner")
                .approvalStatus(StoreStatus.APPROVED).build());
        conversationId = chat.open(store.getStoreId()).get("id").toString();
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void multipartPersistsPrivateFileMessageAndRetryDoesNotDuplicate() throws Exception {
        String key = UUID.randomUUID().toString();
        byte[] bytes = "%PDF-1.7\nfixture".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "hóa đơn.pdf", "application/pdf", bytes);
        mvc.perform(multipart(base()).file(file).param("messageId", key).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(key))
                .andExpect(jsonPath("$.result.messageText").value(org.hamcrest.Matchers.containsString("hóa đơn.pdf")));
        attachments.send(conversationId, key, file);
        assertEquals(1, files.count());
        assertEquals(1, messages.count());
        var original = chat.history(conversationId, 50, null).getFirst();
        // The relay repeats this same message after upload to notify both participants.
        assertEquals(original.getId(), chat.send(conversationId, original.getMessageText(), key).getId());
        assertFalse(chat.messageDto(original).containsKey("content"));
        actor = "owner";
        mvc.perform(get(base() + "/" + key).with(jwt()))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertEquals("application/pdf", attachments.download(conversationId, key).contentType());
    }

    @Test
    void loginAndConversationMembershipProtectUploadsDownloadsAndForgedCards() throws Exception {
        String key = UUID.randomUUID().toString();
        var file = new MockMultipartFile("file", "note.txt", "text/plain", "private note".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart(base()).file(file).param("messageId", key)).andExpect(status().isUnauthorized());
        attachments.send(conversationId, key, file);
        mvc.perform(get(base() + "/" + key)).andExpect(status().isUnauthorized());
        actor = "outsider";
        mvc.perform(get(base() + "/" + key).with(jwt())).andExpect(status().isForbidden());
        mvc.perform(multipart(base()).file(file).param("messageId", UUID.randomUUID().toString()).with(jwt()))
                .andExpect(status().isForbidden());
        actor = "owner";
        assertThrows(ResponseStatusException.class, () -> chat.send(conversationId,
                "{\"type\":\"ATTACHMENT\",\"attachmentId\":\"" + key + "\"}", UUID.randomUUID().toString()));
        actor = "buyer";
        var other = stores.saveAndFlush(Store.builder().name("Other").ownerUserId("owner2").approvalStatus(StoreStatus.APPROVED).build());
        String otherConversation = chat.open(other.getStoreId()).get("id").toString();
        assertThrows(ResponseStatusException.class, () -> attachments.download(otherConversation, key));
        assertThrows(ResponseStatusException.class, () -> chat.send(otherConversation,
                "{\"type\":\"ATTACHMENT\",\"attachmentId\":\"" + key + "\"}", key));
        assertEquals(1, messages.count());
    }

    @Test
    void rejectsDisguisedOversizedAndInvalidFilesWithoutLeavingOrphans() {
        var disguised = new MockMultipartFile("file", "virus.png", "image/png", "executable".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, UUID.randomUUID().toString(), disguised));
        var oversized = new MockMultipartFile("file", "huge.txt", "text/plain", new byte[10 * 1024 * 1024 + 1]);
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> attachments.send(conversationId, UUID.randomUUID().toString(), oversized)).getStatusCode().value());
        var html = new MockMultipartFile("file", "page.html", "text/html", "<script>bad</script>".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, UUID.randomUUID().toString(), html));
        var empty = new MockMultipartFile("file", "note.txt", "text/plain", new byte[0]);
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, UUID.randomUUID().toString(), empty));
        var file = new MockMultipartFile("file", "note.txt", "text/plain", "note".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, "invalid", file));
        String key = UUID.randomUUID().toString();
        chat.send(conversationId, "existing text", key);
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, key, file));
        assertEquals(0, files.count());
        assertEquals(1, messages.count());
    }

    @Test
    void pngMetadataIsCanonicalAndSameKeyCannotReplaceTheSavedFile() {
        String key = UUID.randomUUID().toString();
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jwZkAAAAASUVORK5CYII=");
        var file = new MockMultipartFile("file", "../../ảnh.png", "application/octet-stream", png);
        var sent = attachments.send(conversationId, key, file);
        assertTrue(sent.getMessageText().contains("image/png"));
        assertTrue(sent.getMessageText().contains("IMAGE"));
        assertFalse(sent.getMessageText().contains("../"));
        String forged = "{\"type\":\"ATTACHMENT\",\"attachmentId\":\"" + key + "\",\"fileName\":\"fake.pdf\"}";
        assertEquals(sent.getMessageText(), chat.send(conversationId, forged, key).getMessageText());
        var different = new MockMultipartFile("file", "different.txt", "text/plain", "changed".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, key, different));
        assertArrayEquals(png, attachments.download(conversationId, key).content());
    }

    @Test
    void officeDocumentsRequireTheExpectedPackageAndRejectMacros() throws Exception {
        byte[] document = officePackage("word/document.xml", false);
        var docx = new MockMultipartFile("file", "agreement.docx", "application/octet-stream", document);
        var saved = attachments.send(conversationId, UUID.randomUUID().toString(), docx);
        assertTrue(saved.getMessageText().contains("wordprocessingml.document"));
        var wrongExtension = new MockMultipartFile("file", "sheet.xlsx", "application/zip", document);
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, UUID.randomUUID().toString(), wrongExtension));
        var macro = new MockMultipartFile("file", "macro.docx", "application/zip", officePackage("word/document.xml", true));
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, UUID.randomUUID().toString(), macro));
        var binaryText = new MockMultipartFile("file", "binary.txt", "text/plain", new byte[]{0, 1, 2});
        assertThrows(ResponseStatusException.class, () -> attachments.send(conversationId, UUID.randomUUID().toString(), binaryText));
        assertEquals(1, files.count());
    }

    private byte[] officePackage(String document, boolean macro) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            for (String name : macro ? java.util.List.of("[Content_Types].xml", document, "word/vbaProject.bin")
                    : java.util.List.of("[Content_Types].xml", document)) {
                zip.putNextEntry(new java.util.zip.ZipEntry(name));
                zip.write("<fixture/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String base() {
        return "/api/v1/chat/conversations/" + conversationId + "/attachments";
    }
}
