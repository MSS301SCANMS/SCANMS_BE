package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateCommissionRequest;
import com.scanms.promotion.dto.response.CommissionResponse;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.CommissionMapper;
import com.scanms.promotion.repository.CommissionRepository;
import com.scanms.promotion.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.scanms.promotion.entity.CommissionFinalization;
import com.scanms.promotion.repository.CommissionFinalizationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@RequiredArgsConstructor
@Transactional
public class CommissionServiceImpl implements CommissionService {
    private final CommissionRepository repository;
    private final CommissionMapper mapper;
    private final CommissionFinalizationRepository finalizations;
    private final EntityManager em;
    private final com.scanms.promotion.service.VoucherAccess access;
    private final com.scanms.promotion.repository.CollaboratorProfileRepository profiles;

    public CommissionResponse create(CreateCommissionRequest request) {
        var anchor=em.find(CommissionFinalization.class,request.orderItemId(),LockModeType.PESSIMISTIC_WRITE);
        if(anchor==null) { anchor=new CommissionFinalization(); anchor.setOrderItemId(request.orderItemId()); em.persist(anchor); em.flush(); }
        if(anchor.getFinalizedAt()!=null) throw new AppException(ErrorCode.CONFLICT,"Commission has been finalized; use a referenced adjustment instead");
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public CommissionResponse getById(String id) {
        return findAll().stream().filter(row -> id.equals(row.commissionId())).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Commission not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CommissionResponse> findAll() {
        if(access.isManager()) { access.requireManager(); return repository.findAll().stream().sorted(java.util.Comparator.comparing(com.scanms.promotion.entity.Commission::getCreatedAt,java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))).map(mapper::toResponse).toList(); }
        var profile=profiles.findByUserId(access.userId()).orElseThrow(()->new AppException(ErrorCode.FORBIDDEN,"Bạn chưa có hồ sơ KOL."));
        return repository.findByCollaboratorIdOrderByCreatedAtDesc(profile.getCollaboratorId()).stream().map(mapper::toResponse).toList();
    }
}
