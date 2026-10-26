package com.scanms.user.controller;

import com.scanms.user.constant.UserStatus;
import com.scanms.user.entity.User;
import com.scanms.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:address-book;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN",
        "cloudinary.cloud-name=test","cloudinary.api-key=test","cloudinary.api-secret=test",
        "google.client-id=test","keycloak.client-secret=test"})
class AddressBookIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    User owner;

    @BeforeEach void setup() {
        users.deleteAll();
        owner = users.saveAndFlush(User.builder().identitySubject("address-owner").email("address-owner@example.test")
                .fullName("Original name").phoneNumber("0900000000").status(UserStatus.ACTIVE).build());
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void savesAndReloadsAddressBookBeyondBothFormerLimitsWithoutChangingIdentity() throws Exception {
        String book = "[{\"id\":\"first\",\"detailAddress\":\"" + "Địa chỉ kiểm thử ".repeat(100) + "\",\"isDefault\":true}]";
        assertTrue(book.length() > 500);
        String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("address", book));
        mvc.perform(patch("/api/v1/users").with(jwt().jwt(j -> j.subject("address-owner")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.address").value(book));
        mvc.perform(get("/api/v1/users").with(jwt().jwt(j -> j.subject("address-owner").claim("email",owner.getEmail()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.address").value(book));
        User saved = users.findById(owner.getUserId()).orElseThrow();
        assertEquals(book,saved.getAddress());
        assertEquals("address-owner",saved.getIdentitySubject());
        assertEquals("Original name",saved.getFullName());
        assertEquals("0900000000",saved.getPhoneNumber());
        assertEquals(1,users.count());
    }

    @Test void emptyArrayClearsAddressBookWithoutChangingIdentity() throws Exception {
        owner.setAddress("[{\"id\":\"last\"}]"); users.saveAndFlush(owner);
        mvc.perform(patch("/api/v1/users").with(jwt().jwt(j -> j.subject("address-owner")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"address\":\"[]\"}"))
                .andExpect(status().isOk());
        assertEquals("[]",users.findById(owner.getUserId()).orElseThrow().getAddress());
    }

    @Test void rejectsOversizedBookBeforeWritingIt() throws Exception {
        String body = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("address","x".repeat(65536)));
        mvc.perform(patch("/api/v1/users").with(jwt().jwt(j -> j.subject("address-owner")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("address")));
        assertNull(users.findById(owner.getUserId()).orElseThrow().getAddress());
    }
}
