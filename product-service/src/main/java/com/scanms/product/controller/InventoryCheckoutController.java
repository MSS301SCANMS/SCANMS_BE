package com.scanms.product.controller;
import com.scanms.product.entity.*;
import com.scanms.product.constant.*;
import com.scanms.product.dto.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1/inventory/reservations") @RequiredArgsConstructor @Transactional
public class InventoryCheckoutController {
    private final EntityManager em;
    public record Line(@NotBlank String productId,String variantId,@Min(1) @Max(1000) int quantity) {}
    public record Input(@NotEmpty @Size(max=100) List<@Valid Line> items) {}
    private void internal(Jwt jwt) {
        var roles=new HashSet<String>(); Object realm=jwt.getClaim("realm_access");
        if(realm instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
        Object resources=jwt.getClaim("resource_access");
        if(resources instanceof Map<?,?> map) map.values().forEach(v -> { if(v instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString())); });
        if(!roles.contains("ORDER_INTERNAL")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    @PostMapping("/{orderId}") public ApiResponse<Map<String,Object>> reserve(@PathVariable String orderId,@Valid @RequestBody Input input,@AuthenticationPrincipal Jwt jwt) {
        return reserve(orderId,input,jwt,false);
    }
    @PostMapping("/quote") public ApiResponse<Map<String,Object>> quote(@Valid @RequestBody Input input,@AuthenticationPrincipal Jwt jwt) { return reserve("quote",input,jwt,true); }
    private ApiResponse<Map<String,Object>> reserve(String orderId,Input input,Jwt jwt,boolean preview) {
        internal(jwt);
        // Lock product rows in a fixed order, also serializing attempts for the same order/stock.
        List<String> productIds=input.items().stream().map(Line::productId).distinct().sorted().toList();
        Map<String,Product> products=new HashMap<>();
        for(String id:productIds) {
            Product p=em.find(Product.class,id,LockModeType.PESSIMISTIC_WRITE);
            if(p==null || p.getStatus()!=ProductStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT,"Product is not available"); products.put(id,p);
        }
        List<Line> requested=input.items().stream().sorted(Comparator.comparing(Line::productId).thenComparing(l -> Objects.toString(l.variantId(),""))).toList();
        if(requested.stream().map(l -> l.productId()+":"+Objects.toString(l.variantId(),"")).distinct().count()!=requested.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Merge duplicate cart lines before checkout");
        String hash=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(requested);
        CheckoutReservation existing=preview?null:em.find(CheckoutReservation.class,orderId,LockModeType.PESSIMISTIC_WRITE);
        if(existing!=null) {
            if(!hash.equals(existing.getRequestHash()) || "RELEASED".equals(existing.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation reference belongs to another or released checkout");
            return ApiResponse.success(Map.of("orderId",orderId,"lines",existing.getLines()));
        }
        List<Map<String,Object>> lines=new ArrayList<>();
        for(Line line:requested) {
            Product p=products.get(line.productId()); Store store=em.find(Store.class,p.getStoreId());
            if(store==null || store.getApprovalStatus()!=StoreStatus.APPROVED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Store is not approved");
            ProductVariant variant;
            if(line.variantId()==null || line.variantId().isBlank()) {
                List<ProductVariant> choices=em.createQuery("select v from ProductVariant v where v.productId=:p and v.status=:s",ProductVariant.class).setParameter("p",p.getProductId()).setParameter("s",ProductVariantStatus.ACTIVE).getResultList();
                if(choices.size()!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Select an available product variant"); variant=choices.getFirst(); em.refresh(variant,LockModeType.PESSIMISTIC_WRITE);
            } else variant=em.find(ProductVariant.class,line.variantId(),LockModeType.PESSIMISTIC_WRITE);
            if(variant==null || !p.getProductId().equals(variant.getProductId()) || variant.getStatus()!=ProductVariantStatus.ACTIVE || variant.getPriceVnd()==null || variant.getPriceVnd()<=0 || variant.getStockQuantity()==null || variant.getStockQuantity()<line.quantity()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Variant or stock is unavailable");
            if(!preview) variant.setStockQuantity(variant.getStockQuantity()-line.quantity());
            Map<String,Object> snapshot=new LinkedHashMap<>(); snapshot.put("productId",p.getProductId()); snapshot.put("variantId",variant.getVariantId()); snapshot.put("quantity",line.quantity()); snapshot.put("unitPriceVnd",variant.getPriceVnd()); snapshot.put("grossAmountVnd",Math.multiplyExact(variant.getPriceVnd(),line.quantity())); snapshot.put("storeId",p.getStoreId()); snapshot.put("storeName",store.getName()); snapshot.put("name",p.getName()); snapshot.put("size",variant.getSize()); snapshot.put("color",variant.getColor()); lines.add(snapshot);
        }
        if(!preview) { CheckoutReservation reservation=new CheckoutReservation(); reservation.setOrderId(orderId); reservation.setRequestHash(hash); reservation.setLines(lines); reservation.setStatus("RESERVED"); reservation.setCreatedAt(Instant.now()); em.persist(reservation); em.flush(); }
        return ApiResponse.success(Map.of("orderId",orderId,"lines",lines));
    }
    @PostMapping("/{orderId}/release") public ApiResponse<Map<String,Object>> release(@PathVariable String orderId,@AuthenticationPrincipal Jwt jwt) {
        internal(jwt); CheckoutReservation r=em.find(CheckoutReservation.class,orderId);
        if(r!=null) {
            for(String product:r.getLines().stream().map(l -> l.get("productId").toString()).distinct().sorted().toList()) em.find(Product.class,product,LockModeType.PESSIMISTIC_WRITE);
            em.refresh(r,LockModeType.PESSIMISTIC_WRITE);
        }
        if(r==null || "RELEASED".equals(r.getStatus())) return ApiResponse.success(Map.of("released",true));
        if(!"RESERVED".equals(r.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Committed stock cannot be released");
        for(Map<String,Object> line:r.getLines().stream().sorted(Comparator.comparing(l -> l.get("variantId").toString())).toList()) {
            ProductVariant v=em.find(ProductVariant.class,line.get("variantId").toString(),LockModeType.PESSIMISTIC_WRITE); v.setStockQuantity(Math.addExact(v.getStockQuantity(),((Number)line.get("quantity")).intValue()));
        }
        r.setStatus("RELEASED"); return ApiResponse.success(Map.of("released",true));
    }
    @PostMapping("/{orderId}/commit") public ApiResponse<Map<String,Object>> commit(@PathVariable String orderId,@AuthenticationPrincipal Jwt jwt) {
        internal(jwt); CheckoutReservation r=em.find(CheckoutReservation.class,orderId,LockModeType.PESSIMISTIC_WRITE);
        if(r==null || "RELEASED".equals(r.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock reservation missing");
        r.setStatus("COMMITTED"); return ApiResponse.success(Map.of("committed",true));
    }
}
