package com.scanms.order.service;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.response.LivestreamSalesResponse;
import com.scanms.order.dto.response.StoreSalesResponse;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LivestreamSalesService {
    private final OrderItemRepository items;
    private final SellerOrderRepository sellers;
    private final OrderRepository orders;
    private final RestClient http;

    @Value("${clients.product.url:http://localhost:8082}") private String productUrl;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;

    @Autowired
    public LivestreamSalesService(OrderItemRepository items, SellerOrderRepository sellers,
            OrderRepository orders) {
        this(items, sellers, orders, clientBuilder());
    }

    private static RestClient.Builder clientBuilder() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(factory);
    }

    LivestreamSalesService(OrderItemRepository items, SellerOrderRepository sellers,
            OrderRepository orders, RestClient.Builder builder) {
        this.items = items;
        this.sellers = sellers;
        this.orders = orders;
        this.http = builder.build();
    }

    @Transactional(readOnly = true)
    public LivestreamSalesResponse summary(String livestreamId, Jwt jwt) {
        Map<?, ?> livestream = read(productUrl + "/api/v1/livestreams/{id}", livestreamId, jwt);
        String storeId = Objects.toString(livestream.get("storeId"), "");
        if (storeId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Livestream store is missing");
        requireStoreAccess(storeId, jwt);
        return aggregate(livestreamId, storeId);
    }

    @Transactional(readOnly = true)
    public StoreSalesResponse storeSummary(String storeId, Jwt jwt) {
        requireStoreAccess(storeId, jwt);
        var sellerOrders = sellers.findByStoreIdOrderByCreatedAtDesc(storeId);
        Set<String> orderIds = sellerOrders.stream().map(SellerOrder::getOrderId).collect(Collectors.toSet());
        Map<String, Order> orderById = new HashMap<>();
        orders.findAllById(orderIds).forEach(order -> orderById.put(order.getOrderId(), order));
        Set<String> paidSellerIds = sellerOrders.stream()
                .filter(seller -> seller.getStatus() != SellerOrderStatus.CANCELLED)
                .filter(seller -> {
                    Order order = orderById.get(seller.getOrderId());
                    return order != null && Set.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED)
                            .contains(order.getStatus());
                }).map(SellerOrder::getSellerOrderId).collect(Collectors.toSet());
        long revenue = 0;
        for (OrderItem item : items.findBySellerOrderIdIn(paidSellerIds))
            if (item.getNetPaidAmountVnd() != null) revenue = Math.addExact(revenue, item.getNetPaidAmountVnd());
        return new StoreSalesResponse(storeId, sellerOrders.size(), paidSellerIds.size(), revenue);
    }

    private void requireStoreAccess(String storeId, Jwt jwt) {
        Map<?, ?> user = read(userUrl + "/api/v1/users/me", null, jwt);
        if (!"ACTIVE".equals(user.get("status")) || !jwt.getSubject().equals(user.get("identitySubject")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active user profile required");
        Map<?, ?> store = read(productUrl + "/api/v1/stores/{id}", storeId, jwt);
        if (!storeId.equals(store.get("storeId")))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Livestream store is inconsistent");
        if (!Objects.equals(store.get("ownerUserId"), user.get("userId")) && !hasAdminRole(jwt))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Store owner required");
    }

    private Map<?, ?> read(String uri, String id, Jwt jwt) {
        try {
            Map<?, ?> response = id == null
                    ? http.get().uri(uri).headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class)
                    : http.get().uri(uri, id).headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
            if (response == null || !(response.get("result") instanceof Map<?, ?> result))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Source contract unavailable");
            return result;
        } catch (HttpClientErrorException ex) {
            if (uri.startsWith(userUrl))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active user profile required", ex);
            if (ex.getStatusCode().value() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Livestream or store not found", ex);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sales source access denied", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Livestream sales source unavailable", ex);
        }
    }

    private boolean hasAdminRole(Jwt jwt) {
        Object realm = jwt.getClaim("realm_access");
        if (realm instanceof Map<?, ?> roles && roles.get("roles") instanceof Collection<?> values
                && values.contains("ADMIN")) return true;
        Object resources = jwt.getClaim("resource_access");
        if (resources instanceof Map<?, ?> clients) for (Object client : clients.values()) {
            if (client instanceof Map<?, ?> roles && roles.get("roles") instanceof Collection<?> values
                    && values.contains("ADMIN")) return true;
        }
        return false;
    }

    LivestreamSalesResponse aggregate(String livestreamId, String storeId) {
        var attributed = items.findBySourceLivestreamId(livestreamId);
        Set<String> sellerIds = attributed.stream().map(OrderItem::getSellerOrderId).collect(Collectors.toSet());
        Map<String, SellerOrder> sellerById = new HashMap<>();
        sellers.findAllById(sellerIds).forEach(seller -> sellerById.put(seller.getSellerOrderId(), seller));
        Set<String> orderIds = sellerById.values().stream().map(SellerOrder::getOrderId).collect(Collectors.toSet());
        Map<String, Order> orderById = new HashMap<>();
        orders.findAllById(orderIds).forEach(order -> orderById.put(order.getOrderId(), order));
        Set<String> buyers = new HashSet<>(), countedOrders = new HashSet<>();
        long quantity = 0, gmv = 0, netSales = 0;
        for (OrderItem item : attributed) {
            SellerOrder seller = sellerById.get(item.getSellerOrderId());
            if (seller == null || !storeId.equals(seller.getStoreId()) || seller.getStatus() == SellerOrderStatus.CANCELLED)
                continue;
            Order order = orderById.get(seller.getOrderId());
            if (order == null || !Set.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED).contains(order.getStatus()))
                continue;
            countedOrders.add(order.getOrderId());
            if (order.getCustomerId() != null) buyers.add(order.getCustomerId());
            quantity = Math.addExact(quantity, item.getQuantity());
            gmv = Math.addExact(gmv, item.getGrossAmountVnd());
            netSales = Math.addExact(netSales, item.getNetPaidAmountVnd());
        }
        return new LivestreamSalesResponse(livestreamId, storeId, buyers.size(), countedOrders.size(), quantity, gmv, netSales);
    }
}
