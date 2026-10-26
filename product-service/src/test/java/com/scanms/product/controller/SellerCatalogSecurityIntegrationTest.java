package com.scanms.product.controller;

import com.scanms.product.entity.*;
import com.scanms.product.constant.*;
import com.scanms.product.repository.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:seller-catalog-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","scanms.catalog.seed-enabled=false","scanms.catalog.cache-enabled=false","logging.level.root=WARN","debug=false"})
class SellerCatalogSecurityIntegrationTest {
    @Autowired WebApplicationContext context;@Autowired ProductRepository products;@Autowired StoreRepository stores;
    @Autowired ProductImageRepository images;@Autowired ProductVariantRepository variants;
    @MockitoBean JwtDecoder decoder;
    MockMvc mvc;Store a,b;Product pa,pb;ProductImage ib;ProductVariant vb;
    static final HttpServer source=start();
    static HttpServer start(){try{var s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);s.createContext("/api/v1/users",x->{
        String user=Objects.toString(x.getRequestHeaders().getFirst("Authorization"),"").replace("Bearer ","");
        byte[] body=("{\"result\":{\"userId\":\""+user+"\",\"identitySubject\":\""+user+"\",\"status\":\"ACTIVE\"}}").getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();});s.start();return s;}catch(Exception ex){throw new IllegalStateException(ex);}}
    @DynamicPropertySource static void urls(DynamicPropertyRegistry r){r.add("clients.user.url",()->"http://127.0.0.1:"+source.getAddress().getPort());}
    @AfterAll static void close(){source.stop(0);}
    @BeforeEach void setup(){
        images.deleteAll();variants.deleteAll();products.deleteAll();stores.deleteAll();
        a=stores.saveAndFlush(Store.builder().name("Shop A").ownerUserId("seller-a").approvalStatus(StoreStatus.APPROVED).build());
        b=stores.saveAndFlush(Store.builder().name("Shop B").ownerUserId("seller-b").approvalStatus(StoreStatus.APPROVED).build());
        pa=products.saveAndFlush(Product.builder().name("A").storeId(a.getStoreId()).priceVnd(100L).status(ProductStatus.ACTIVE).build());
        pb=products.saveAndFlush(Product.builder().name("Private draft B").storeId(b.getStoreId()).priceVnd(200L).status(ProductStatus.INACTIVE).build());
        ib=images.saveAndFlush(ProductImage.builder().productId(pb.getProductId()).imageUrl("https://example.test/b.jpg").objectKey("b").primaryImage(true).sortOrder(0).build());
        vb=variants.saveAndFlush(ProductVariant.builder().productId(pb.getProductId()).sku("B-M").priceVnd(200L).stockQuantity(10).status(ProductVariantStatus.ACTIVE).build());
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor actor(String subject,String role){return jwt().jwt(Jwt.withTokenValue(subject).header("alg","RS256").subject(subject).claim("realm_access",Map.of("roles",List.of(role))).build());}
    String json(Object value){return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value);}
    @Test void sellerCannotReadOrChangeAnotherShopsDraftImagesOrVariants()throws Exception{
        mvc.perform(get("/api/v1/products").with(actor("seller-a","SHOP_OWNER"))).andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].productId").value(pa.getProductId()));
        for(String path:List.of("products/"+pb.getProductId(),"product-images/"+ib.getImageId(),"product-variants/"+vb.getVariantId())){
            mvc.perform(get("/api/v1/"+path).with(actor("seller-a","SHOP_OWNER"))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/"+path).with(actor("seller-a","SHOP_OWNER")).contentType("application/json").content("{\"name\":\"Tampered\",\"stockQuantity\":0,\"sortOrder\":99}")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/"+path).with(actor("seller-a","SHOP_OWNER"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/product-images").with(actor("seller-a","SHOP_OWNER"))).andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(get("/api/v1/product-variants").with(actor("seller-a","SHOP_OWNER"))).andExpect(jsonPath("$.result").isEmpty());
        assertEquals(10,variants.findById(vb.getVariantId()).orElseThrow().getStockQuantity());
        mvc.perform(get("/api/v1/products").with(actor("customer","USER"))).andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(get("/api/v1/products").with(actor("manager","SYSTEM_MANAGER"))).andExpect(jsonPath("$.result.length()").value(2));
        mvc.perform(get("/api/v1/products/"+pb.getProductId()).with(actor("order-service","ORDER_INTERNAL"))).andExpect(status().isOk());
    }
    @Test void ownerManagesPrimaryImageAndVariantButSuspensionBlocksWrites()throws Exception{
        var body=Map.of("productId",pa.getProductId(),"objectKey","front","imageUrl","https://example.test/front.jpg","primaryImage",true,"sortOrder",1);
        var response=mvc.perform(post("/api/v1/product-images").with(actor("seller-a","SHOP_OWNER")).contentType("application/json").content(json(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String first=tools.jackson.databind.json.JsonMapper.builder().build().readTree(response).path("result").path("imageId").asText();
        var second=images.saveAndFlush(ProductImage.builder().productId(pa.getProductId()).objectKey("back").imageUrl("https://example.test/back.jpg").sortOrder(2).primaryImage(false).build());
        mvc.perform(patch("/api/v1/product-images/"+second.getImageId()).with(actor("seller-a","SHOP_OWNER")).contentType("application/json").content("{\"primaryImage\":true,\"sortOrder\":0}")).andExpect(status().isOk());
        assertFalse(images.findById(first).orElseThrow().isPrimaryImage());
        mvc.perform(delete("/api/v1/product-images/"+second.getImageId()).with(actor("seller-a","SHOP_OWNER"))).andExpect(status().isNoContent());
        assertTrue(images.findById(first).orElseThrow().isPrimaryImage());
        var variant=Map.of("productId",pa.getProductId(),"sku","A-M","size","M","color","White","priceVnd",150,"stockQuantity",5,"status","ACTIVE");
        mvc.perform(post("/api/v1/product-variants").with(actor("seller-a","SHOP_OWNER")).contentType("application/json").content(json(variant))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/product-variants").with(actor("seller-a","SHOP_OWNER")).contentType("application/json").content(json(variant))).andExpect(status().isConflict());
        a.setApprovalStatus(StoreStatus.SUSPENDED);stores.saveAndFlush(a);
        mvc.perform(get("/api/v1/products/"+pa.getProductId()).with(actor("seller-a","SHOP_OWNER"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/product-images").with(actor("seller-a","SHOP_OWNER")).contentType("application/json").content(json(body))).andExpect(status().isConflict());
    }
}
