package com.scanms.promotion.repository;

import com.scanms.promotion.entity.SampleRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SampleRequestRepository extends JpaRepository<SampleRequest, String> {
    List<SampleRequest> findByCustomerIdOrderByCreatedAtDesc(String id);

    List<SampleRequest> findByStoreIdInOrderByCreatedAtDesc(Collection<String> ids);

    boolean existsByCollaboratorIdAndProductIdAndStatusIn(String kol, String product, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SampleRequest s where s.id=:id")
    Optional<SampleRequest> locked(@Param("id") String id);
}
