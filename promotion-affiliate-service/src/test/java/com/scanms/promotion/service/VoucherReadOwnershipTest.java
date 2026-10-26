package com.scanms.promotion.service;

import com.scanms.promotion.entity.Voucher;
import com.scanms.promotion.mapper.VoucherMapper;
import com.scanms.promotion.repository.VoucherRepository;
import com.scanms.promotion.service.impl.VoucherServiceImpl;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherReadOwnershipTest {
    final VoucherRepository repository=mock(VoucherRepository.class);
    final VoucherAccess access=mock(VoucherAccess.class);
    final VoucherService service=new VoucherServiceImpl(repository,Mappers.getMapper(VoucherMapper.class),access);
    final Voucher own=Voucher.builder().voucherId("own").code("OWN").storeId("shop-a").build();
    final Voucher other=Voucher.builder().voucherId("other").code("OTHER").storeId("shop-b").build();
    final Voucher platform=Voucher.builder().voucherId("platform").code("PLATFORM").build();

    @Test void privateSellerReadsExcludeOtherStoresAndPlatformAdministration() {
        when(repository.findAll()).thenReturn(List.of(own,other,platform));
        when(access.ownedStoreIds()).thenReturn(Set.of("shop-a"));
        assertEquals(List.of("own"),service.findAll().stream().map(v->v.voucherId()).toList());
        when(repository.findById("other")).thenReturn(Optional.of(other));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(AppException.class,()->service.getById("other")).getErrorCode());
        when(repository.findById("platform")).thenReturn(Optional.of(platform));
        assertEquals(ErrorCode.FORBIDDEN,assertThrows(AppException.class,()->service.getById("platform")).getErrorCode());
        when(access.ownedStoreIds()).thenReturn(Set.of());
        assertTrue(service.findAll().isEmpty());
    }

    @Test void trustedManagerOrInternalReadsCanResolveAllIssuers() {
        when(access.canReadAllVouchers()).thenReturn(true);
        when(repository.findAll()).thenReturn(List.of(own,other,platform));
        when(repository.findById("platform")).thenReturn(Optional.of(platform));
        assertEquals(3,service.findAll().size());
        assertEquals("platform",service.getById("platform").voucherId());
        verify(access,never()).ownedStoreIds();
    }
}
