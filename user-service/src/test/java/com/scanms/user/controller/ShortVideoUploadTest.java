package com.scanms.user.controller;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ShortVideoUploadTest {
    Cloudinary cloud;UploadController controller;Jwt jwt;
    @BeforeEach void setup(){cloud=mock(Cloudinary.class,RETURNS_DEEP_STUBS);controller=new UploadController(cloud);jwt=Jwt.withTokenValue("test").header("alg","RS256").subject("creator-subject").build();}
    @Test void checksOwnerBeforeLookingUpResource() throws Exception {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.verifyShortVideo("scanms/short-videos/other-user/asset",jwt));
        verifyNoInteractions(cloud);
    }
    @Test void usesServerDurationAndRejectsOverlongMedia() throws Exception {
        var file=new MockMultipartFile("file","short.mp4","video/mp4",new byte[]{1,2,3});
        when(cloud.uploader().upload(any(byte[].class),anyMap())).thenReturn(new HashMap<>(Map.of("public_id","scanms/short-videos/creator-subject/asset","secure_url","https://res.cloudinary.com/test/video/upload/v1/asset.mp4","duration",181.0)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.uploadShortVideo(file,jwt));
        verify(cloud.uploader()).destroy(eq("scanms/short-videos/creator-subject/asset"),anyMap());
    }
    @Test void createsPlayableAssetAndPosterAndRejectsNonVideoFiles() throws Exception {
        var asset=new HashMap<>(Map.of("public_id","scanms/short-videos/creator-subject/asset","secure_url","https://res.cloudinary.com/test/video/upload/v1/asset.mp4","duration",30.0));
        when(cloud.uploader().upload(any(byte[].class),anyMap())).thenReturn(asset);
        var result=controller.uploadShortVideo(new MockMultipartFile("file","short.mp4","video/mp4",new byte[]{1}),jwt).result();
        assertEquals(30.0,result.get("durationSeconds"));assertEquals("https://res.cloudinary.com/test/video/upload/so_0/v1/asset.jpg",result.get("posterUrl"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->controller.uploadShortVideo(new MockMultipartFile("file","image.png","image/png",new byte[]{1}),jwt));
    }
}
