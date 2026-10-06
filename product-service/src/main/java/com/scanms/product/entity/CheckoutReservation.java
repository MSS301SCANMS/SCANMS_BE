package com.scanms.product.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="checkout_reservations") @Getter @Setter @NoArgsConstructor
public class CheckoutReservation {
    @Id private String orderId;
    @Column(columnDefinition="text") private String requestHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<Map<String,Object>> lines;
    private String status;
    private Instant createdAt;
}
