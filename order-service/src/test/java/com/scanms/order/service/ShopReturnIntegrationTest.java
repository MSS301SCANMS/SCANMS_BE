package com.scanms.order.service;

import com.scanms.order.constant.*;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:shop-return;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF", "debug=false"
})
class ShopReturnIntegrationTest {
    @Autowired ShopReturnService service;
    @Autowired ReturnRequestRepository returns;
    @Autowired OrderItemRepository items;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderRepository orders;
    @Autowired WebApplicationContext context;
    @MockitoBean ShopReturnAccess access;
    @MockitoBean JwtDecoder decoder;
    private final Jwt token=Jwt.withTokenValue("fixture").header("alg","RS256").subject("shop-owner").build();

    @BeforeEach void setup() {
        returns.deleteAll(); items.deleteAll(); sellers.deleteAll(); orders.deleteAll();
        when(access.stores(any())).thenReturn(Map.of("store-a","Gian hàng A"));
    }
    ReturnRequest row(String store) {
        var order=orders.saveAndFlush(Order.builder().customerId("customer").currency("VND").status(OrderStatus.PAID)
            .payableVnd(100_000L).shippingSnapshot(Map.of("name","Khách hàng","phone","0900000000")).build());
        var seller=sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId(store).status(SellerOrderStatus.DELIVERED)
            .deliveredAt(LocalDateTime.now().minusDays(2)).returnDeadline(LocalDateTime.now().plusDays(12)).build());
        var item=items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).productId("product").quantity(2).unitPrice(50_000L)
            .productSnapshot(Map.of("productName","Áo kiểm thử","imageUrl","https://example.test/product.png")).build());
        return returns.saveAndFlush(ReturnRequest.builder().orderItemId(item.getOrderItemId()).quantity(1).reason("Sai sản phẩm")
            .status(ReturnRequestStatus.REQUESTED).requestedAt(LocalDateTime.now()).refundAmountVnd(50_000L)
            .evidenceRefs(Map.of("images",List.of("https://example.test/customer.png"),"shopResponse",Map.of("response","forged customer claim"))).build());
    }
    @Test void queueOnlyIncludesOwnedStoresAndUsesPersistedDeliveryDeadline() {
        var own=row("store-a"); row("store-b");
        var result=service.list(token);
        assertEquals(1,result.size());
        var view=result.getFirst();
        assertEquals(own.getReturnRequestId(),view.returnRequestId());
        assertEquals("Áo kiểm thử",view.productName());
        assertEquals("Khách hàng",view.customerName());
        assertEquals(50_000L,view.refundAmountVnd());
        assertNotNull(view.returnDeadline());
        assertNull(view.shopResponse(),"Customer evidence must never impersonate a shop statement");
        when(access.stores(any())).thenReturn(Map.of());
        assertTrue(service.list(token).isEmpty());
    }
    @Test void responsePersistsWithoutDecidingOrRefundingAndIdenticalRetryIsIdempotent() {
        var own=row("store-a");
        var input=new ShopReturnService.ResponseInput("Đã kiểm tra đóng gói",List.of("https://example.test/shop.png"));
        var first=service.respond(own.getReturnRequestId(),input,token);
        var retry=service.respond(own.getReturnRequestId(),input,token);
        assertEquals(first.shopResponse(),retry.shopResponse());
        assertEquals(first.shopResponse(),service.list(token).getFirst().shopResponse());
        var saved=returns.findById(own.getReturnRequestId()).orElseThrow();
        assertEquals(ReturnRequestStatus.REQUESTED,saved.getStatus());
        assertNull(saved.getRefundReference()); assertNull(saved.getResolvedAt());
        assertEquals(own.getEvidenceRefs(),saved.getEvidenceRefs());
        assertThrows(ResponseStatusException.class,()->service.respond(own.getReturnRequestId(),new ShopReturnService.ResponseInput("Changed",List.of()),token));
    }
    @Test void foreignReturnsAndFinalizedReturnsCannotBeChanged() {
        var foreign=row("store-b");
        var input=new ShopReturnService.ResponseInput("Statement",List.of());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.respond(foreign.getReturnRequestId(),input,token)).getStatusCode().value());
        var own=row("store-a"); own.setStatus(ReturnRequestStatus.REJECTED); returns.saveAndFlush(own);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.respond(own.getReturnRequestId(),input,token)).getStatusCode().value());
    }
    @Test void evidenceValidationFailsBeforePersistingAnything() {
        var own=row("store-a");
        for(var image:List.of("javascript:alert(1)","data:image/png;base64,fixture","https://user:secret@example.test/image.png"))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->service.respond(own.getReturnRequestId(),new ShopReturnService.ResponseInput("Statement",List.of(image)),token)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->service.respond(own.getReturnRequestId(),new ShopReturnService.ResponseInput("   ",List.of()),token));
        assertNull(returns.findById(own.getReturnRequestId()).orElseThrow().getShopResponse());
    }
    @Test void httpRequiresShopRoleAndValidatesInputs() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/returns/shop")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/returns/shop").with(jwt().jwt(jwt -> jwt.claim("realm_access",Map.of("roles",List.of("USER")))))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/returns/shop").with(jwt().jwt(jwt -> jwt.claim("realm_access",Map.of("roles",List.of("SHOP_OWNER")))))).andExpect(status().isOk());
        var own=row("store-a");
        mvc.perform(post("/api/v1/returns/"+own.getReturnRequestId()+"/shop-response").with(jwt().jwt(jwt -> jwt.claim("realm_access",Map.of("roles",List.of("SHOP_OWNER"))))).contentType("application/json").content("{\"response\":\"\",\"imageUrls\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/returns/"+own.getReturnRequestId()+"/shop-response").with(jwt().jwt(jwt -> jwt.claim("realm_access",Map.of("roles",List.of("SHOP_OWNER"))))).contentType("application/json").content("{\"response\":\"Shop statement\",\"imageUrls\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.shopResponse.response").value("Shop statement"));
    }
}
