package com.scanms.order.controller;

import com.scanms.order.constant.*;
import com.scanms.order.dto.ApiResponse;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.math.BigInteger;
import java.util.*;

/** Boundary owned by Order: Payment reads validated amounts, never client-supplied totals. */
@RestController @RequestMapping("/api/v1/finance") @RequiredArgsConstructor @Transactional
public class FinanceContractController {
    private final EntityManager em;
    private final OrderRepository orders;
    private final SellerOrderRepository sellers;
    private final OrderItemRepository items;
    private final ReturnRequestRepository returns;
    private final DiscountAllocationRepository allocations;
    private void trusted(Jwt jwt,boolean operatorAllowed) {
        Set<String> roles=new HashSet<>();
        Object realm=jwt.getClaim("realm_access");
        if(realm instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
        Object resources=jwt.getClaim("resource_access");
        if(resources instanceof Map<?,?> map) map.values().forEach(v -> { if(v instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString())); });
        if(!roles.contains("PAYMENT_INTERNAL") && !(operatorAllowed && roles.stream().anyMatch(Set.of("ADMIN","MANAGER","SYSTEM_ADMIN","SYSTEM_MANAGER")::contains))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private <T> T find(Class<T> type,String id,boolean lock) {
        T result=lock?em.find(type,id,LockModeType.PESSIMISTIC_WRITE):em.find(type,id);
        if(result==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return result;
    }
    public record PaidInput(String paymentId,Long amountVnd,String currency,String verifiedAt) {}
    @PostMapping("/orders/{id}/paid") ApiResponse<Map<String,Object>> paid(@PathVariable String id,@RequestBody PaidInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,false); Order order=find(Order.class,id,true);
        if(input.paymentId()==null || !Objects.equals(input.amountVnd(),order.getPayableVnd()) || !Objects.equals(input.currency(),order.getCurrency())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment total mismatch");
        Map<String,Object> saga=new LinkedHashMap<>(order.getSagaState()==null?Map.of():order.getSagaState());
        if(saga.get("paymentId")!=null && !input.paymentId().equals(saga.get("paymentId"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"A different payment already paid this order; reconcile excess payment");
        if(order.getStatus()==OrderStatus.CANCELLED || order.getStatus()==OrderStatus.EXPIRED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Late payment requires reconciliation");
        saga.put("paymentId",input.paymentId()); saga.put("verifiedAt",input.verifiedAt()); order.setSagaState(saga);
        if(order.getStatus()==OrderStatus.PENDING || order.getStatus()==OrderStatus.AWAITING_PAYMENT) order.setStatus(OrderStatus.PAID);
        orders.save(order); return ApiResponse.success(Map.of("orderId",id,"status",order.getStatus()));
    }
    @GetMapping("/refund-basis/{id}") ApiResponse<Map<String,Object>> refundBasis(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); ReturnRequest request=find(ReturnRequest.class,id,false);
        OrderItem item=find(OrderItem.class,request.getOrderItemId(),true);
        em.refresh(request,LockModeType.PESSIMISTIC_WRITE);
        SellerOrder seller=find(SellerOrder.class,item.getSellerOrderId(),false); Order order=find(Order.class,seller.getOrderId(),false);
        if(!Set.of(ReturnRequestStatus.APPROVED,ReturnRequestStatus.RECEIVED,ReturnRequestStatus.REFUND_PENDING,ReturnRequestStatus.REFUNDED).contains(request.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Return is not approved");
        if(order.getStatus()!=OrderStatus.PAID && order.getStatus()!=OrderStatus.PROCESSING && order.getStatus()!=OrderStatus.COMPLETED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Order has not been paid");
        if(request.getRequestedAt()==null || seller.getReturnDeadline()==null || request.getRequestedAt().isAfter(seller.getReturnDeadline())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Return is outside the delivery return window");
        if(!"VND".equals(order.getCurrency())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only VND refunds are supported");
        long amount;
        if(request.getStatus()==ReturnRequestStatus.REFUND_PENDING || request.getStatus()==ReturnRequestStatus.REFUNDED) {
            if(request.getRefundAmountVnd()==null || request.getRefundAmountVnd()<=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund snapshot missing; reconcile legacy data");
            amount=request.getRefundAmountVnd();
        } else {
            int reservedQuantity=0; long reservedAmount=0;
            for(ReturnRequest r:returns.findAll().stream().filter(r -> item.getOrderItemId().equals(r.getOrderItemId()) && !id.equals(r.getReturnRequestId())).toList()) {
                if(r.getStatus()==ReturnRequestStatus.REFUND_PENDING || r.getStatus()==ReturnRequestStatus.REFUNDED || r.getRefundReference()!=null) {
                    if(r.getQuantity()==null || r.getRefundAmountVnd()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Existing refund reservation is incomplete");
                    reservedQuantity=Math.addExact(reservedQuantity,r.getQuantity()); reservedAmount=Math.addExact(reservedAmount,r.getRefundAmountVnd());
                }
            }
            try { amount=com.scanms.order.service.RefundAllocation.allocate(item.getNetPaidAmountVnd(),item.getQuantity(),request.getQuantity(),reservedQuantity,reservedAmount); }
            catch(IllegalArgumentException | NullPointerException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund exceeds remaining paid quantity/value"); }
            request.setRefundAmountVnd(amount); request.setStatus(ReturnRequestStatus.REFUND_PENDING); returns.save(request);
        }
        return ApiResponse.success(Map.of("returnRequestId",id,"orderItemId",item.getOrderItemId(),"customerId",order.getCustomerId(),"amountVnd",amount,"currency",order.getCurrency()));
    }
    public record RefundInput(String transactionId,Long amountVnd) {}
    @GetMapping("/orders/{id}/unapplied-payment") ApiResponse<Map<String,Object>> unapplied(@PathVariable String id,@RequestParam String paymentId,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); Order order=find(Order.class,id,true);
        Object applied=(order.getSagaState()==null?Map.of():order.getSagaState()).get("paymentId");
        boolean refundable=(order.getStatus()==OrderStatus.CANCELLED || order.getStatus()==OrderStatus.EXPIRED || (applied!=null && !paymentId.equals(applied))) && !paymentId.equals(applied);
        return ApiResponse.success(Map.of("refundable",refundable,"customerId",order.getCustomerId(),"amountVnd",order.getPayableVnd(),"currency",order.getCurrency()));
    }
    @PostMapping("/returns/{id}/refunded") ApiResponse<Map<String,Object>> refunded(@PathVariable String id,@RequestBody RefundInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,false); ReturnRequest request=find(ReturnRequest.class,id,true);
        if(input.transactionId()==null || !Objects.equals(input.amountVnd(),request.getRefundAmountVnd())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund reference mismatch");
        if(request.getRefundReference()!=null && !request.getRefundReference().equals(input.transactionId())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Return already has another refund");
        if(request.getStatus()!=ReturnRequestStatus.REFUND_PENDING && request.getStatus()!=ReturnRequestStatus.REFUNDED) throw new ResponseStatusException(HttpStatus.CONFLICT);
        request.setRefundReference(input.transactionId()); request.setStatus(ReturnRequestStatus.REFUNDED); request.setResolvedAt(LocalDateTime.now(ZoneOffset.UTC)); returns.save(request);
        return ApiResponse.success(Map.of("returnRequestId",id,"status","REFUNDED"));
    }
    @GetMapping("/settlement-basis/{id}") ApiResponse<Map<String,Object>> settlementBasis(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); SellerOrder seller=find(SellerOrder.class,id,true); Order order=find(Order.class,seller.getOrderId(),false);
        if(seller.getStatus()!=SellerOrderStatus.COMPLETED || seller.getDeliveredAt()==null || seller.getReturnDeadline()==null
                || seller.getReturnDeadline().isBefore(seller.getDeliveredAt().plusDays(14)) || !seller.getReturnDeadline().isBefore(LocalDateTime.now(ZoneOffset.UTC))
                || !Set.of(OrderStatus.PAID,OrderStatus.PROCESSING,OrderStatus.COMPLETED).contains(order.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Seller order is not eligible");
        List<OrderItem> lines=items.findAll().stream().filter(i -> id.equals(i.getSellerOrderId())).toList();
        if(lines.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Seller order has no items");
        long gross=0,refund=0,sellerDiscount=0,platformDiscount=0,subsidy=0,netPaid=0;
        List<String> refs=new ArrayList<>();
        for(OrderItem item:lines) {
            refs.add(item.getOrderItemId()); gross=Math.addExact(gross,item.getGrossAmountVnd()); netPaid=Math.addExact(netPaid,item.getNetPaidAmountVnd());
            List<ReturnRequest> requests=returns.findAll().stream().filter(r -> item.getOrderItemId().equals(r.getOrderItemId())).toList();
            int quantityReturned=0;
            for(ReturnRequest r:requests) {
                if(!Set.of(ReturnRequestStatus.REJECTED,ReturnRequestStatus.CANCELLED,ReturnRequestStatus.REFUNDED).contains(r.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Unresolved return blocks settlement");
                if(r.getStatus()==ReturnRequestStatus.REFUNDED) { refund=Math.addExact(refund,r.getRefundAmountVnd()); quantityReturned=Math.addExact(quantityReturned,r.getQuantity()); }
            }
            long allocated=0;
            for(DiscountAllocation a:allocations.findAll().stream().filter(a -> item.getOrderItemId().equals(a.getOrderItemId())).toList()) {
                allocated=Math.addExact(allocated,a.getAllocatedAmountVnd());
                long platformShare=a.getFundedBy()==DiscountFundingType.PLATFORM?a.getAllocatedAmountVnd():0;
                if(a.getFundedBy()==DiscountFundingType.SHARED) {
                    Map<?,?> rule=tools.jackson.databind.json.JsonMapper.builder().build().readValue(a.getRuleSnapshotJson(),Map.class);
                    if(!(rule.get("platformFundedVnd") instanceof Number)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Shared funding snapshot missing");
                    platformShare=new java.math.BigDecimal(rule.get("platformFundedVnd").toString()).longValueExact();
                    if(platformShare<0 || platformShare>a.getAllocatedAmountVnd()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid shared funding");
                }
                platformDiscount=Math.addExact(platformDiscount,platformShare);
                sellerDiscount=Math.addExact(sellerDiscount,a.getAllocatedAmountVnd()-platformShare);
                subsidy=Math.addExact(subsidy,platformShare-portion(platformShare,quantityReturned,item.getQuantity()));
            }
            if(allocated!=item.getDiscountAmountVnd() || item.getGrossAmountVnd()-allocated!=item.getNetPaidAmountVnd()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Discount allocation does not match paid snapshot");
        }
        // GrossRevenue here is gross before seller-funded discount, after platform-funded discount.
        return ApiResponse.success(Map.of("sellerOrderId",id,"storeId",seller.getStoreId(),"orderItemIds",String.join(",",refs),
                "grossRevenueVnd",gross-platformDiscount,"refundVnd",refund,"sellerDiscountVnd",sellerDiscount,
                "platformSubsidyVnd",subsidy,"feeBasisVnd",netPaid-refund,"returnDeadline",seller.getReturnDeadline().toString()));
    }
    static long portion(Long total,int quantity,int purchased) {
        if(total==null || total<0 || purchased<=0 || quantity<0 || quantity>purchased) throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid item value/quantity");
        return BigInteger.valueOf(total).multiply(BigInteger.valueOf(quantity)).divide(BigInteger.valueOf(purchased)).longValueExact();
    }
    @GetMapping("/commission-order-item/{id}") ApiResponse<Map<String,Object>> commissionItem(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); OrderItem item=find(OrderItem.class,id,true);
        settlementBasis(item.getSellerOrderId(),jwt);
        long refund=0;
        for(ReturnRequest r:returns.findAll().stream().filter(r -> id.equals(r.getOrderItemId()) && r.getStatus()==ReturnRequestStatus.REFUNDED).toList()) refund=Math.addExact(refund,r.getRefundAmountVnd());
        long retained=Math.subtractExact(item.getNetPaidAmountVnd(),refund);
        if(retained<0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund exceeds paid item value");
        return ApiResponse.success(Map.of("orderItemId",id,"basisAmountVnd",retained,"currency","VND","commissionApplicable",item.getReferralLinkId()!=null || (item.getRuleSnapshot()!=null && item.getRuleSnapshot().get("commissionPolicyId")!=null)));
    }
}
