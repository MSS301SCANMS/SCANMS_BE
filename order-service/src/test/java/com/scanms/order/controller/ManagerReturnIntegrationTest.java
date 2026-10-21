package com.scanms.order.controller;

import com.scanms.order.constant.*;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import com.scanms.order.service.ManagerStatisticsAccess;
import com.scanms.order.client.PaymentClient;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:manager-returns;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class ManagerReturnIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderItemRepository items;
    @Autowired ReturnRequestRepository returns;
    @MockitoBean ManagerStatisticsAccess access;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PaymentClient payment;
    MockMvc mvc; ReturnRequest row;
    @BeforeEach void setup() {
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        returns.deleteAll();items.deleteAll();sellers.deleteAll();orders.deleteAll();
        var order=orders.saveAndFlush(Order.builder().status(OrderStatus.PAID).shippingSnapshot(Map.of("name","Khách kiểm thử","phone","0900000000")).build());
        var seller=sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId("store-test").status(SellerOrderStatus.DELIVERED).totalsSnapshot(Map.of("storeName","Shop kiểm thử")).build());
        var item=items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).productId("product-test").quantity(2).netPaidAmountVnd(100000L).productSnapshot(Map.of("name","Sản phẩm kiểm thử")).build());
        row=returns.saveAndFlush(ReturnRequest.builder().orderItemId(item.getOrderItemId()).quantity(1).refundAmountVnd(50000L).reason("Hàng lỗi").status(ReturnRequestStatus.REQUESTED).requestedAt(LocalDateTime.now().minusHours(49)).evidenceRefs(Map.of("images",java.util.List.of("https://example.test/customer.png"))).shopResponse(Map.of("response","Giải trình của shop","imageUrls",java.util.List.of("https://example.test/shop.png"))).build());
    }
    @Test void loadsRealOrderAndShopEvidenceThenApprovesWithoutCallingPayment() throws Exception {
        mvc.perform(get("/api/v1/returns/manager").with(jwt())).andExpect(status().isOk())
            .andExpect(jsonPath("$.result[0].storeName").value("Shop kiểm thử"))
            .andExpect(jsonPath("$.result[0].productName").value("Sản phẩm kiểm thử"))
            .andExpect(jsonPath("$.result[0].shopResponse.response").value("Giải trình của shop"))
            .andExpect(jsonPath("$.result[0].evidenceRefs.images[0]").value("https://example.test/customer.png"));
        String body="{\"status\":\"APPROVED\",\"notes\":\"Chấp thuận dựa trên bằng chứng hàng bị lỗi.\"}";
        for(int retry=0;retry<2;retry++) mvc.perform(post("/api/v1/returns/manager/"+row.getReturnRequestId()+"/decision").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("APPROVED"));
        assertNull(returns.findById(row.getReturnRequestId()).orElseThrow().getRefundReference());
        verifyNoInteractions(payment);
        mvc.perform(post("/api/v1/returns/manager/"+row.getReturnRequestId()+"/decision").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body.replace("APPROVED","REJECTED"))).andExpect(status().isConflict());
    }
    @Test void rejectsInvalidDecisionsShortReasonsAndUnauthorizedAccess() throws Exception {
        mvc.perform(get("/api/v1/returns/manager")).andExpect(status().isUnauthorized());
        String path="/api/v1/returns/manager/"+row.getReturnRequestId()+"/decision";
        mvc.perform(post(path).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUNDED\",\"notes\":\"Không cho phép giả mạo hoàn tiền thành công.\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"notes\":\"ngắn\"}")).andExpect(status().isBadRequest());
        assertEquals(ReturnRequestStatus.REQUESTED,returns.findById(row.getReturnRequestId()).orElseThrow().getStatus());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager required")).when(access).requireManager(any());
        mvc.perform(get("/api/v1/returns/manager").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\",\"notes\":\"Bằng chứng đầy đủ để chấp thuận yêu cầu.\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(payment);
    }
}
