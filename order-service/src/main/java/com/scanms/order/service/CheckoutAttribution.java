package com.scanms.order.service;
import java.util.*;
public interface CheckoutAttribution {
    Map<String,Object> verify(CheckoutService.Line line,String token,String customerId);
}
