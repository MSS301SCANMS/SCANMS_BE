package com.scanms.order.repository;

import com.scanms.order.dto.response.DeliveryNotificationSource;
import com.scanms.order.entity.OrderNotificationRead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderNotificationReadRepository extends JpaRepository<OrderNotificationRead, String> {
    boolean existsByUserIdAndSellerOrderId(String userId, String sellerOrderId);

    @Query(value = """
            select new com.scanms.order.dto.response.DeliveryNotificationSource(
                s.sellerOrderId, o.orderId, o.customerId, s.storeId, s.deliveredAt,
                case when r.id is null then false else true end)
            from SellerOrder s join Order o on o.orderId = s.orderId
            left join OrderNotificationRead r on r.sellerOrderId = s.sellerOrderId and r.userId = o.customerId
            where o.customerId = :userId and s.deliveredAt is not null
                and (:sellerId is null or s.sellerOrderId = :sellerId)
            order by s.deliveredAt desc, s.sellerOrderId desc
            """, countQuery = """
            select count(s) from SellerOrder s join Order o on o.orderId = s.orderId
            where o.customerId = :userId and s.deliveredAt is not null
                and (:sellerId is null or s.sellerOrderId = :sellerId)
            """)
    Page<DeliveryNotificationSource> deliveries(@Param("userId") String userId, @Param("sellerId") String sellerId, Pageable pageable);

    @Query("""
            select s.sellerOrderId from SellerOrder s join Order o on o.orderId = s.orderId
            where o.customerId = :userId and s.deliveredAt is not null
              and not exists (select r.id from OrderNotificationRead r
                  where r.userId = :userId and r.sellerOrderId = s.sellerOrderId)
            order by s.sellerOrderId
            """)
    List<String> unreadSellerIds(@Param("userId") String userId);

    @Query("""
            select count(s) from SellerOrder s join Order o on o.orderId = s.orderId
            where o.customerId = :userId and s.deliveredAt is not null
              and not exists (select r.id from OrderNotificationRead r
                  where r.userId = :userId and r.sellerOrderId = s.sellerOrderId)
            """)
    long unreadCount(@Param("userId") String userId);
}
