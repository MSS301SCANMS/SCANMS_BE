package com.scanms.product.repository;

import com.scanms.product.entity.Livestream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface LivestreamRepository extends JpaRepository<Livestream, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select live from Livestream live where live.livestreamId = :id")
    Optional<Livestream> lockById(@Param("id") String id);
    long countByStoreId(String storeId);
    @Query("select live.livestreamId from Livestream live where live.status in :statuses order by live.livestreamId")
    java.util.List<String> findIdsByStatusIn(@Param("statuses") java.util.Collection<com.scanms.product.constant.LivestreamStatus> statuses);
}
