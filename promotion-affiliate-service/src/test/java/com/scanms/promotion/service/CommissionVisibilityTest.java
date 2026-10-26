package com.scanms.promotion.service;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.mapper.CommissionMapper;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.impl.CommissionServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommissionVisibilityTest {
    final CommissionRepository rows=mock(CommissionRepository.class);
    final VoucherAccess access=mock(VoucherAccess.class);
    final CollaboratorProfileRepository profiles=mock(CollaboratorProfileRepository.class);
    final CommissionService service=new CommissionServiceImpl(rows,Mappers.getMapper(CommissionMapper.class),mock(CommissionFinalizationRepository.class),mock(EntityManager.class),access,profiles);
    final Commission mine=Commission.builder().commissionId("mine").collaboratorId("kol-one").orderItemId("item").basisAmountVnd(1000000L).rateSnapshot(BigDecimal.TEN).commissionAmountVnd(100000L).status(CommissionStatus.PENDING).build();

    @BeforeEach void setup() {
        when(access.userId()).thenReturn("user-one");
        when(profiles.findByUserId("user-one")).thenReturn(Optional.of(CollaboratorProfile.builder().collaboratorId("kol-one").build()));
        when(rows.findByCollaboratorIdOrderByCreatedAtDesc("kol-one")).thenReturn(List.of(mine));
    }
    @Test void kolSeesOnlyOwnCommissionsAndResponseContainsFinancialValues() {
        var result=service.findAll();
        assertEquals(1,result.size()); assertEquals("mine",result.getFirst().commissionId());
        assertEquals(1000000L,result.getFirst().basisVnd()); assertEquals(BigDecimal.TEN,result.getFirst().rate()); assertEquals(100000L,result.getFirst().amountVnd());
        assertThrows(AppException.class,()->service.getById("another-kols-row"));
        verify(rows,never()).findAll();
    }
    @Test void operatorMustPassActiveAccountValidationBeforeReadingAllCommissions() {
        when(access.isManager()).thenReturn(true); when(rows.findAll()).thenReturn(List.of(mine));
        assertEquals(1,service.findAll().size()); verify(access).requireManager();
        doThrow(new IllegalStateException("inactive account")).when(access).requireManager();
        assertThrows(IllegalStateException.class,()->service.findAll());
        verify(rows,times(1)).findAll();
    }
    @Test void customerWithoutKolProfileCannotReadCommissions() {
        when(profiles.findByUserId("user-one")).thenReturn(Optional.empty());
        assertThrows(AppException.class,()->service.findAll()); verifyNoInteractions(rows);
    }
}
