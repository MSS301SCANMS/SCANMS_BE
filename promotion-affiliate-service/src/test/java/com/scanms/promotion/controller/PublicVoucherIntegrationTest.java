package com.scanms.promotion.controller;
import com.scanms.promotion.entity.Voucher;
import com.scanms.promotion.constant.*;
import com.scanms.promotion.repository.VoucherRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import java.time.*;
import java.math.BigDecimal;
import java.util.Map;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:public-vouchers;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","logging.level.root=WARN"})
class PublicVoucherIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired VoucherRepository vouchers;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;
    @BeforeEach void setup(){mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();vouchers.deleteAll();}
    Voucher voucher(String code,String store,LocalDateTime from,LocalDateTime until,VoucherIssuerType issuer){return Voucher.builder().code(code).storeId(store).issuerType(issuer).scopeType(VoucherScopeType.STORE).discountType(VoucherDiscountType.FIXED).value(BigDecimal.valueOf(10000)).validFrom(from).validUntil(until).funding(Map.of("internal","must-not-leak")).limits(Map.of("internal","must-not-leak")).build();}
    @Test void anonymousStorefrontOnlyReceivesCurrentStoreVouchersAndNoInternalFundingData() throws Exception {
        var now=LocalDateTime.now(ZoneOffset.UTC);
        vouchers.save(voucher("PUBLIC","store-a",now.minusDays(1),now.plusDays(1),VoucherIssuerType.STORE));
        vouchers.save(voucher("EXPIRED","store-a",now.minusDays(2),now.minusDays(1),VoucherIssuerType.STORE));
        vouchers.save(voucher("FUTURE","store-a",now.plusDays(1),now.plusDays(2),VoucherIssuerType.STORE));
        vouchers.save(voucher("OTHER","store-b",now.minusDays(1),now.plusDays(1),VoucherIssuerType.STORE));
        vouchers.save(voucher("PLATFORM","store-a",now.minusDays(1),now.plusDays(1),VoucherIssuerType.PLATFORM));
        vouchers.flush();
        mvc.perform(get("/api/v1/vouchers/public").param("storeId","store-a")).andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].code").value("PUBLIC")).andExpect(jsonPath("$.result[0].funding").doesNotExist()).andExpect(jsonPath("$.result[0].limits").doesNotExist());
        mvc.perform(get("/api/v1/vouchers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vouchers/public").param("storeId"," ")).andExpect(status().isBadRequest());
    }

    @Test void platformSuggestionsExposeOnlyCurrentGoodsAndShippingForTheApplicableStore() throws Exception {
        var now=LocalDateTime.now(ZoneOffset.UTC);
        var global=voucher("SAN10",null,now.minusDays(1),now.plusDays(1),VoucherIssuerType.PLATFORM);
        global.setScopeType(VoucherScopeType.PLATFORM); global.setLimits(null); vouchers.save(global);
        var ship=voucher("FREESHIP","store-a",now.minusDays(1),now.plusDays(1),VoucherIssuerType.PLATFORM);
        ship.setScope(Map.of("benefitType","SHIPPING")); ship.setLimits(Map.of("totalUsageLimit",3)); vouchers.save(ship);
        vouchers.save(voucher("SHOP","store-a",now.minusDays(1),now.plusDays(1),VoucherIssuerType.STORE));
        vouchers.save(voucher("OTHER","store-b",now.minusDays(1),now.plusDays(1),VoucherIssuerType.PLATFORM));
        vouchers.save(voucher("OLD","store-a",now.minusDays(2),now.minusDays(1),VoucherIssuerType.PLATFORM));
        var cashback=voucher("CASHBACK","store-a",now.minusDays(1),now.plusDays(1),VoucherIssuerType.PLATFORM);
        cashback.setScope(Map.of("benefitType","CASHBACK")); vouchers.save(cashback); vouchers.flush();
        mvc.perform(get("/api/v1/vouchers/public/platform").param("storeId","store-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].code").value("FREESHIP"))
                .andExpect(jsonPath("$.result[0].benefitType").value("SHIPPING"))
                .andExpect(jsonPath("$.result[0].remainingUses").value(3))
                .andExpect(jsonPath("$.result[0].funding").doesNotExist())
                .andExpect(jsonPath("$.result[0].limits").doesNotExist());
        mvc.perform(get("/api/v1/vouchers/public/platform"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].code").value("SAN10"));
    }
}
