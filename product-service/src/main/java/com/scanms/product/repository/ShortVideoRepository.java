package com.scanms.product.repository;

import com.scanms.product.entity.ShortVideo;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ShortVideoRepository extends JpaRepository<ShortVideo,String> {
    Optional<ShortVideo> findByPublicId(String publicId);
    Page<ShortVideo> findByCreatorUserIdOrderByCreatedAtDesc(String userId,Pageable pageable);
    @Query("select v from ShortVideo v where v.status='PUBLISHED' and exists (select s from Store s where s.storeId=v.storeId and s.approvalStatus=com.scanms.product.constant.StoreStatus.APPROVED) order by v.createdAt desc,v.id desc")
    Page<ShortVideo> feed(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from ShortVideo v where v.id=:id")
    Optional<ShortVideo> lock(@Param("id") String id);
}
