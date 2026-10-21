package com.scanms.order.controller;

import com.scanms.order.constant.*;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import com.scanms.order.service.ManagerStatisticsAccess;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:manager-statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN", "scanms.finance.client-secret="})
class ManagerStatisticsIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired OrderRepository orders;
    @Autowired SellerOrderRepository sellers;
    @Autowired OrderItemRepository items;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ManagerStatisticsAccess access;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        items.deleteAll(); sellers.deleteAll(); orders.deleteAll();
    }
    void orderAt(String vietnamTime, long amount) {
        var order=orders.saveAndFlush(Order.builder().status(OrderStatus.PAID).payableVnd(amount).currency("VND").build());
        var local=LocalDateTime.parse(vietnamTime).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        jdbc.update("update orders set created_at=? where order_id=?",local,order.getOrderId());
        var seller=sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId("store-test").status(SellerOrderStatus.COMPLETED).totalsSnapshot(Map.of("storeName","Cửa hàng kiểm thử")).build());
        items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).productId("product-test").quantity(1).netPaidAmountVnd(amount).productSnapshot(Map.of("name","Sản phẩm kiểm thử")).build());
    }
    @Test void endpointUsesHalfOpenVietnamCalendarBoundsAndDoesNotMatchOrderIdRoute() throws Exception {
        orderAt("2026-09-30T23:59:59",999); orderAt("2026-10-01T00:00:00",100); orderAt("2026-10-31T23:59:59",200); orderAt("2026-11-01T00:00:00",888);
        mvc.perform(get("/api/v1/orders/statistics?range=month&anchor=2026-10-21").with(jwt()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.orderCount").value(2)).andExpect(jsonPath("$.result.revenueVnd").value(300))
            .andExpect(jsonPath("$.result.fromDate").value("2026-10-01")).andExpect(jsonPath("$.result.toDate").value("2026-10-31"))
            .andExpect(jsonPath("$.result.daily[0].revenueVnd").value(100)).andExpect(jsonPath("$.result.daily[30].revenueVnd").value(200))
            .andExpect(jsonPath("$.result.topStores[0].name").value("Cửa hàng kiểm thử"));
        mvc.perform(get("/api/v1/orders/statistics?range=month&anchor=2026-08-21").with(jwt())).andExpect(status().isOk()).andExpect(jsonPath("$.result.revenueVnd").value(0));
    }
    @Test void validatesPeriodAndRequiresManagerBeforeReadingData() throws Exception {
        mvc.perform(get("/api/v1/orders/statistics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/statistics?range=invalid").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders/statistics?anchor=not-a-date").with(jwt())).andExpect(status().isBadRequest());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager required")).when(access).requireManager(any());
        mvc.perform(get("/api/v1/orders/statistics").with(jwt())).andExpect(status().isForbidden());
    }
}
