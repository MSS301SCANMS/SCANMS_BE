package com.scanms.promotion.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.*;
@Entity @Table(name="checkout_vouchers") @Getter @Setter @NoArgsConstructor
public class CheckoutVoucher {
    @Id private String orderId;
    private String customerId;
    private String voucherId;
    @Column(columnDefinition="text") private String requestHash;
    private String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private List<Map<String,Object>> allocations;
}
