package com.scanms.product.service;

import com.scanms.product.client.*;
import com.scanms.product.repository.*;
import com.scanms.product.entity.*;
import com.scanms.product.constant.*;
import com.scanms.product.dto.*;
import com.scanms.product.dto.ShortVideoContracts.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:short-videos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","scanms.catalog.seed-enabled=false","scanms.catalog.cache-enabled=false","logging.level.root=WARN"})
class ShortVideoIntegrationTest {
    @Autowired ShortVideoService service;
    @Autowired ShortVideoRepository videos;
    @Autowired ProductRepository products;
    @Autowired StoreRepository stores;
    @MockitoBean ProductAccess access;
    @MockitoBean ShortVideoAffiliateClient affiliate;
    @MockitoBean ShortVideoUploadClient uploads;
    @MockitoBean JwtDecoder decoder;
    String store,product;
    @BeforeEach void setup(){
        videos.deleteAll();products.deleteAll();stores.deleteAll();
        store=stores.saveAndFlush(Store.builder().name("Shop").ownerUserId("shop-user").approvalStatus(StoreStatus.APPROVED).build()).getStoreId();
        product=products.saveAndFlush(Product.builder().name("Shirt").storeId(store).status(ProductStatus.ACTIVE).priceVnd(270000L).build()).getProductId();
        when(access.userId()).thenReturn("creator");when(access.bearerToken()).thenReturn("creator-jwt");
        when(affiliate.verify(anyString(),eq(store),anyList())).thenReturn(ApiResponse.success(Map.of("userId","creator","collaboratorId","kol","creatorName","Creator")));
        when(uploads.verify("Bearer creator-jwt","asset")).thenReturn(ApiResponse.success(Map.of("secureUrl","https://res.cloudinary.com/test/video/upload/short.mp4","durationSeconds",30.0,"posterUrl","https://res.cloudinary.com/test/video/upload/short.jpg")));
    }
    Create input(){return new Create("Product video","My caption","asset",List.of(new Link(product,"referral")));}
    @Test void publishesImmediatelyRetainsAttributionAndRetriesWithoutDuplicate(){
        var created=service.create(input());assertEquals("PUBLISHED",created.status());assertEquals("Creator",created.creatorName());
        assertEquals("referral",created.products().getFirst().referralLinkId());assertEquals(270000,created.products().getFirst().priceVnd());
        assertEquals(created.id(),service.create(input()).id());assertEquals(1,videos.count());assertEquals(created.id(),service.feed(0).items().getFirst().id());
        verify(affiliate,times(1)).verify(anyString(),eq(store),anyList());
    }
    @Test void cannotPublishWithoutVerifiedKolOrUseAnotherOwnersAsset(){
        when(affiliate.verify(anyString(),anyString(),anyList())).thenThrow(feign.FeignException.errorStatus("access",feign.Response.builder().status(403).reason("Forbidden").request(feign.Request.create(feign.Request.HttpMethod.POST,"http://test",Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null)).build()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(input()));assertEquals(0,videos.count());
    }
    @Test void hidingRequiresOwnershipAndRemovesFromPublicFeed(){
        var created=service.create(input());when(access.userId()).thenReturn("stranger");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.hide(created.id()));
        when(access.userId()).thenReturn("creator");service.hide(created.id());assertTrue(service.feed(0).items().isEmpty());assertEquals("HIDDEN",service.mine(0).items().getFirst().status());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.publicVideo(created.id()));
    }
    @Test void rejectsMixedStoresAndDuplicateProductsAndHidesUnavailableProducts(){
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(new Create("Video","","asset",List.of(new Link(product,"r"),new Link(product,"r")))));
        var other=products.saveAndFlush(Product.builder().name("Other").storeId("other-store").status(ProductStatus.ACTIVE).priceVnd(100L).build());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create(new Create("Video","","asset",List.of(new Link(product,"r"),new Link(other.getProductId(),"r2")))));
        service.create(input());var p=products.findById(product).orElseThrow();p.setStatus(ProductStatus.INACTIVE);products.saveAndFlush(p);
        assertTrue(service.feed(0).items().isEmpty());
    }
}
