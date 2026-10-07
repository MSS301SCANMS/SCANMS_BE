package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.entity.FeeConfig;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.repository.FeeConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceRulesTest {
    @Test void percentageRoundsVndAndAppliesLimits() {
        var calculator=new FeeCalculator(mock(FeeConfigRepository.class));
        var fee=FeeConfig.builder().calculationType(FeeCalculationType.PERCENTAGE).ratePercent(new BigDecimal("2.5")).build();
        assertEquals(3,calculator.calculate(fee,100)); fee.setMinFeeVnd(10L); assertEquals(10,calculator.calculate(fee,100));
        fee.setMaxFeeVnd(100L); assertEquals(100,calculator.calculate(fee,100_000));
    }
    @Test void overlappingActivePeriodsAreRejectedButAdjacentAreAccepted() {
        var repository=mock(FeeConfigRepository.class); var calculator=new FeeCalculator(repository);
        var first=FeeConfig.builder().feeConfigId("one").feeType(FeeType.PLATFORM_FEE).calculationType(FeeCalculationType.FIXED).fixedAmountVnd(100L)
                .status(FeeConfigStatus.ACTIVE).validFrom(Instant.parse("2026-01-01T00:00:00Z")).validTo(Instant.parse("2026-02-01T00:00:00Z")).build();
        when(repository.findAll()).thenReturn(List.of(first));
        var next=FeeConfig.builder().feeConfigId("two").feeType(FeeType.PLATFORM_FEE).calculationType(FeeCalculationType.FIXED).fixedAmountVnd(100L)
                .status(FeeConfigStatus.ACTIVE).validFrom(Instant.parse("2026-01-31T00:00:00Z")).build();
        assertThrows(AppException.class,() -> calculator.validate(next)); next.setValidFrom(first.getValidTo()); assertDoesNotThrow(() -> calculator.validate(next));
    }
    @Test void ciphertextIsRandomizedAndTamperingFails() {
        var cipher=new BankCipher(); ReflectionTestUtils.setField(cipher,"key","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        String first=cipher.encrypt("123456789"); assertNotEquals(first,cipher.encrypt("123456789")); assertEquals("123456789",cipher.decrypt(first));
        assertThrows(AppException.class,() -> cipher.decrypt(first.substring(0,first.length()-4)+"AAAA"));
    }
    @Test void webhookSigningIsSortedAndTamperingRejected() throws Exception {
        var provider=new PayosProvider(); ReflectionTestUtils.setField(provider,"clientId","client"); ReflectionTestUtils.setField(provider,"apiKey","api"); ReflectionTestUtils.setField(provider,"checksum","secret");
        Map<String,Object> body=Map.of("amount",10000,"code","00","currency","VND","orderCode",123);
        Mac independent=Mac.getInstance("HmacSHA256"); independent.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String expected=HexFormat.of().formatHex(independent.doFinal("amount=10000&code=00&currency=VND&orderCode=123".getBytes(StandardCharsets.UTF_8)));
        assertDoesNotThrow(() -> provider.verifyWebhook(body,expected)); assertThrows(AppException.class,() -> provider.verifyWebhook(Map.of("amount",1,"code","00","currency","VND","orderCode",123),expected));
    }
    @Test void weekendAndConfiguredHolidayDelayBankTransferOnly() {
        var calendar=new BankBusinessCalendar(); ReflectionTestUtils.setField(calendar,"closedDates","2026-10-05");
        Instant saturday=Instant.parse("2026-10-03T04:00:00Z");
        assertEquals(Instant.parse("2026-10-06T02:00:00Z"),calendar.nextProcessingAt(saturday));
    }
    @Test void payoutNotificationSignatureUsesItsOwnKeyAndNestedCanonicalData() throws Exception {
        var provider=new PayosProvider(); ReflectionTestUtils.setField(provider,"payoutClient","client"); ReflectionTestUtils.setField(provider,"payoutApiKey","api"); ReflectionTestUtils.setField(provider,"payoutChecksum","payout-secret");
        Map<String,Object> body=Map.of("referenceId","withdrawal", "transactions",List.of(Map.of("toBin","970422","amount",1000)));
        String canonical="referenceId=withdrawal&transactions=%5B%7B%22amount%22:1000,%22toBin%22:%22970422%22%7D%5D";
        Mac independent=Mac.getInstance("HmacSHA256");independent.init(new SecretKeySpec("payout-secret".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String expected=HexFormat.of().formatHex(independent.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        assertDoesNotThrow(()->provider.verifyPayoutNotification(body,expected));
        assertThrows(AppException.class,()->provider.verifyPayoutNotification(Map.of("referenceId","other","transactions",body.get("transactions")),expected));
    }
    @Test void persistedBankHolidayDelaysProcessingWithoutChangingWallets() {
        var calendar=new BankBusinessCalendar(); ReflectionTestUtils.setField(calendar,"closedDates","");
        var repository=org.mockito.Mockito.mock(com.scanms.payment.repository.BankClosedDayRepository.class);
        var holiday=new com.scanms.payment.entity.BankClosedDay();holiday.setClosedDate(java.time.LocalDate.of(2026,10,5));
        org.mockito.Mockito.when(repository.findAll()).thenReturn(java.util.List.of(holiday));ReflectionTestUtils.setField(calendar,"days",repository);
        assertEquals(Instant.parse("2026-10-06T02:00:00Z"),calendar.nextProcessingAt(Instant.parse("2026-10-03T04:00:00Z")));
    }
}
