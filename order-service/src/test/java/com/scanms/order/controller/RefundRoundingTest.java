package com.scanms.order.controller;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RefundRoundingTest {
    @Test void threePartialReturnsPreserveEveryVnd() {
        long first=FinanceContractController.portion(100L,1,3);
        long second=FinanceContractController.portion(100L,2,3)-first;
        long third=FinanceContractController.portion(100L,3,3)-first-second;
        assertEquals(33,first); assertEquals(33,second); assertEquals(34,third); assertEquals(100,first+second+third);
    }
    @Test void quantityCannotExceedPurchasedOrBeNegative() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> FinanceContractController.portion(100L,4,3));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> FinanceContractController.portion(100L,-1,3));
    }
}
