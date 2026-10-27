package com.scanms.order.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class GhnSandboxShippingTest {
    HttpServer server;GhnSandboxShipping ghn;AtomicInteger calls=new AtomicInteger();AtomicBoolean fail=new AtomicBoolean();
    AtomicReference<Map<?,?>> request=new AtomicReference<>();
    CheckoutService.ShippingDestination address(String ward) {return new CheckoutService.ShippingDestination("Thành phố Hồ Chí Minh","Huyện Hóc Môn",ward,"Trưng Vương 2");}
    List<Map<String,Object>> lines(int quantity) {return List.of(Map.of("productId","shirt","storeId","sora","quantity",quantity,"grossAmountVnd",299000L*quantity));}
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            assertEquals("qa-only-token",exchange.getRequestHeaders().getFirst("Token"));assertEquals("123",exchange.getRequestHeaders().getFirst("ShopId"));
            String path=exchange.getRequestURI().getPath(),body;
            switch(path) {
                case "/master-data/province" -> body="[{\"ProvinceID\":202,\"ProvinceName\":\"Hồ Chí Minh\",\"NameExtension\":[\"TP.HCM\"]}]";
                case "/master-data/district" -> body="[{\"DistrictID\":1455,\"DistrictName\":\"Hóc Môn\"}]";
                case "/master-data/ward" -> body="[{\"WardCode\":\"ba-diem\",\"WardName\":\"Bà Điểm\"},{\"WardCode\":\"tan-xuan\",\"WardName\":\"Tân Xuân\"}]";
                case "/v2/shop/all" -> body="{\"shops\":[{\"_id\":123,\"status\":1,\"district_id\":1444,\"ward_code\":\"origin-ward\"}]}";
                case "/v2/shipping-order/available-services" -> body="[{\"service_type_id\":2}]";
                case "/v2/shipping-order/fee" -> {
                    calls.incrementAndGet();var input=JsonMapper.builder().build().readValue(exchange.getRequestBody().readAllBytes(),Map.class);request.set(input);
                    if(fail.get()){byte[] bytes="{\"code\":401,\"message\":\"qa-only-token\"}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(401,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();return;}
                    long price="ba-diem".equals(input.get("to_ward_code"))?25000:35000;
                    body="{\"total\":"+price+"}";
                }
                default -> throw new AssertionError("Unexpected carrier endpoint: "+path);
            }
            byte[] bytes=("{\"code\":200,\"data\":"+body+"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        ghn=new GhnSandboxShipping();
        ReflectionTestUtils.setField(ghn,"provider","GHN_SANDBOX");ReflectionTestUtils.setField(ghn,"token","qa-only-token");ReflectionTestUtils.setField(ghn,"configuredShopId","123");ReflectionTestUtils.setField(ghn,"storeId","sora");
        ReflectionTestUtils.setField(ghn,"itemWeight",300);ReflectionTestUtils.setField(ghn,"length",25);ReflectionTestUtils.setField(ghn,"width",20);ReflectionTestUtils.setField(ghn,"height",8);
        ReflectionTestUtils.setField(ghn,"baseUrl","http://127.0.0.1:"+server.getAddress().getPort());
    }
    @AfterEach void close(){server.stop(0);}
    @Test void usesCarrierCodesAndServerParcelWithDifferentAddressQuotes() {
        assertTrue(ghn.enabled());var first=ghn.quote("sora",address("Xã Bà Điểm"),lines(1));
        assertEquals(25000,first.fee());assertEquals("GHN_SANDBOX",first.snapshot().get("shippingMode"));
        assertEquals(1455,request.get().get("to_district_id"));assertEquals("ba-diem",request.get().get("to_ward_code"));
        assertEquals(300,request.get().get("weight"));assertEquals(299000,request.get().get("insurance_value"));
        assertFalse(request.get().containsKey("to_phone"));assertFalse(request.get().containsKey("to_address"));
        assertEquals(25000,ghn.quote("sora",address("Xã Bà Điểm"),lines(1)).fee());assertEquals(1,calls.get());
        assertEquals(35000,ghn.quote("sora",address("Xã Tân Xuân"),lines(1)).fee());assertEquals(2,calls.get());
        ghn.quote("sora",address("Xã Bà Điểm"),lines(2));assertEquals(600,request.get().get("weight"));
    }
    @Test void carrierFailureIsNeverFreeShippingAndDoesNotLeakCredentials() {
        fail.set(true);
        var ex=assertThrows(ResponseStatusException.class,()->ghn.quote("sora",address("Bà Điểm"),lines(1)));
        assertEquals(503,ex.getStatusCode().value());assertFalse(ex.getReason().contains("qa-only-token"));
    }
    @Test void missingConfigurationAddressUnknownStoreAndHeavyParcelAreRejected() {
        ReflectionTestUtils.setField(ghn,"token","");assertThrows(ResponseStatusException.class,()->ghn.quote("sora",address("Bà Điểm"),lines(1)));
        ReflectionTestUtils.setField(ghn,"token","qa-only-token");
        assertThrows(ResponseStatusException.class,()->ghn.quote("other",address("Bà Điểm"),lines(1)));
        assertThrows(ResponseStatusException.class,()->ghn.quote("sora",null,lines(1)));
        assertThrows(ResponseStatusException.class,()->ghn.quote("sora",address("Unknown ward"),lines(1)));
        assertThrows(ResponseStatusException.class,()->ghn.quote("sora",address("Bà Điểm"),lines(100)));
        assertEquals(0,calls.get());
    }
}
