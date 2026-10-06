package com.scanms.order.service;

import java.math.BigInteger;

/** Allocates against immutable reservations, including refunds awaiting their acknowledgement. */
public final class RefundAllocation {
    private RefundAllocation() {}
    public static long allocate(long paid,int purchased,int quantity,int reservedQuantity,long reservedAmount) {
        if(paid<0 || purchased<=0 || quantity<=0 || reservedQuantity<0 || reservedAmount<0
                || reservedQuantity>purchased || quantity>purchased-reservedQuantity || reservedAmount>paid)
            throw new IllegalArgumentException("Refund exceeds the remaining paid quantity/value");
        long remaining=paid-reservedAmount;
        long amount=quantity==purchased-reservedQuantity ? remaining
                : BigInteger.valueOf(paid).multiply(BigInteger.valueOf(quantity)).divide(BigInteger.valueOf(purchased)).longValueExact();
        if(amount<=0 || amount>remaining) throw new IllegalArgumentException("No refundable paid value remains");
        return amount;
    }
}
