package com.scanms.promotion.service;

import com.scanms.promotion.constant.VoucherDiscountType;
import com.scanms.promotion.constant.VoucherIssuerType;
import com.scanms.promotion.constant.VoucherScopeType;
import com.scanms.promotion.dto.request.CreateVoucherRequest;
import com.scanms.promotion.entity.Voucher;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.VoucherMapper;
import com.scanms.promotion.repository.VoucherRepository;
import com.scanms.promotion.service.impl.VoucherServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoucherCreationRulesTest {
    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final VoucherAccess access = mock(VoucherAccess.class);
    private final VoucherServiceImpl service = new VoucherServiceImpl(vouchers, new VoucherMapper(), access);

    private CreateVoucherRequest request(VoucherIssuerType issuer, VoucherScopeType scope, Map<String, Object> funding) {
        return new CreateVoucherRequest("save10", issuer, scope == VoucherScopeType.PLATFORM ? null : "store-1",
                null, null, scope, VoucherDiscountType.FIXED, BigDecimal.TEN, null, 0L,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), Map.of("totalUsageLimit", 1), null, funding);
    }

    private CreateVoucherRequest withRules(BigDecimal value, Map<String, Object> limits, Map<String, Object> funding) {
        return withRules(VoucherIssuerType.STORE, VoucherScopeType.STORE, value, limits, funding);
    }

    private CreateVoucherRequest withRules(VoucherIssuerType issuer, VoucherScopeType scope,
            BigDecimal value, Map<String, Object> limits, Map<String, Object> funding) {
        var base = request(issuer, scope, funding);
        return new CreateVoucherRequest(base.code(), base.issuerType(), base.storeId(), base.productId(),
                base.livestreamId(), base.scopeType(), base.discountType(), value, base.cap(),
                base.minimumSubtotal(), base.validFrom(), base.validUntil(), limits, base.scope(), funding);
    }

    @Test void sellerCannotIssuePlatformFundedVoucher() {
        assertThrows(AppException.class, () -> service.create(request(VoucherIssuerType.STORE,
                VoucherScopeType.STORE, Map.of("fundedBy", "PLATFORM"))));
        verify(vouchers, never()).save(any());
    }

    @Test void platformVoucherRequiresManagerRole() {
        doThrow(new AppException(ErrorCode.FORBIDDEN)).when(access).requireManager();
        assertThrows(AppException.class, () -> service.create(request(VoucherIssuerType.PLATFORM,
                VoucherScopeType.PLATFORM, Map.of("fundedBy", "PLATFORM"))));
        verify(vouchers, never()).save(any());
    }

    @Test void validStoreVoucherHasCanonicalCodeAndOwnerChecks() {
        when(vouchers.save(any(Voucher.class))).thenAnswer(call -> call.getArgument(0));
        var created = service.create(request(VoucherIssuerType.STORE, VoucherScopeType.STORE, Map.of("fundedBy", "STORE")));
        assertEquals("SAVE10", created.code());
        verify(access).requireStoreOwner("store-1");
        verify(access).requireApprovedStore("store-1");
    }

    @Test void fixedDiscountAndUsageLimitsMustBeWholeNumbers() {
        assertThrows(AppException.class, () -> service.create(withRules(new BigDecimal("10.5"),
                Map.of("totalUsageLimit", 1), Map.of("fundedBy", "STORE"))));
        assertThrows(AppException.class, () -> service.create(withRules(BigDecimal.TEN,
                Map.of("perCustomerLimit", 1.5), Map.of("fundedBy", "STORE"))));
        verify(vouchers, never()).save(any());
    }

    @Test void sharedFundingRejectsNonFiniteRate() {
        assertThrows(AppException.class, () -> service.create(withRules(VoucherIssuerType.PLATFORM, VoucherScopeType.PLATFORM, BigDecimal.TEN,
                Map.of("totalUsageLimit", 1), Map.of("fundedBy", "SHARED", "platformRatePercent", Double.NaN))));
        verify(vouchers, never()).save(any());
    }
}
