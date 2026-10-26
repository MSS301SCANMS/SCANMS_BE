package com.scanms.user.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.scanms.user.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final Cloudinary cloudinary;

    @PostMapping("/short-video")
    public ApiResponse<Map<String,Object>> uploadShortVideo(@RequestParam("file") MultipartFile file,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) throws IOException {
        if(file.isEmpty() || file.getSize()>100L*1024*1024 || !java.util.Set.of("video/mp4","video/webm","video/quicktime").contains(file.getContentType()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Chọn video MP4, WebM hoặc MOV tối đa 100 MB.");
        String folder=shortVideoFolder(jwt);
        Map<?,?> asset=cloudinary.uploader().upload(file.getBytes(),ObjectUtils.asMap("folder",folder,"resource_type","video"));
        double duration=asset.get("duration") instanceof Number n?n.doubleValue():0;
        if(duration<=0 || duration>180) {
            try { cloudinary.uploader().destroy(asset.get("public_id").toString(),ObjectUtils.asMap("resource_type","video")); } catch(Exception ignored) { }
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Video ngắn cần có thời lượng từ 1 đến 180 giây.");
        }
        return ApiResponse.success(shortVideoAsset(asset));
    }

    @org.springframework.web.bind.annotation.GetMapping("/short-video/verify")
    public ApiResponse<Map<String,Object>> verifyShortVideo(@RequestParam String publicId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) throws Exception {
        String prefix=shortVideoFolder(jwt)+"/";
        if(!publicId.startsWith(prefix)||!publicId.substring(prefix.length()).matches("[A-Za-z0-9_-]{1,200}"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Video không thuộc tài khoản hiện tại.");
        Map<?,?> asset=cloudinary.api().resource(publicId,ObjectUtils.asMap("resource_type","video"));
        return ApiResponse.success(shortVideoAsset(asset));
    }

    private String shortVideoFolder(org.springframework.security.oauth2.jwt.Jwt jwt) {
        if(jwt==null || !jwt.getSubject().matches("[A-Za-z0-9_-]{1,100}"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return "scanms/short-videos/"+jwt.getSubject();
    }

    private Map<String,Object> shortVideoAsset(Map<?,?> asset) {
        double duration=asset.get("duration") instanceof Number n?n.doubleValue():0;
        String url=java.util.Objects.toString(asset.get("secure_url"),"");
        if(duration<=0||duration>180||!url.startsWith("https://res.cloudinary.com/"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Video chưa hợp lệ hoặc vượt quá 180 giây.");
        String poster=url.replaceFirst("/video/upload/","/video/upload/so_0/").replaceFirst("\\.[A-Za-z0-9]+$",".jpg");
        return Map.of("publicId",asset.get("public_id"),"secureUrl",url,"posterUrl",poster,"durationSeconds",duration);
    }

    @PostMapping("/image")
    public ApiResponse<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "scanms/images") String folder)
            throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File khong duoc de trong");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chi chap nhan file anh (JPEG, PNG, WEBP)");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Dung luong file khong duoc vuot qua 5MB");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image"
        ));

        Map<String, Object> response = Map.of(
                "publicId", uploadResult.get("public_id"),
                "url", uploadResult.get("url"),
                "secureUrl", uploadResult.get("secure_url"),
                "format", uploadResult.getOrDefault("format", ""),
                "bytes", uploadResult.getOrDefault("bytes", 0)
        );

        return ApiResponse.success(response);
    }

    @PostMapping("/video")
    public ApiResponse<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "scanms/videos") String folder)
            throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File khong duoc de trong");
        }

        if (file.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("Dung luong video khong duoc vuot qua 100MB");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "video"
        ));

        Map<String, Object> response = Map.of(
                "publicId", uploadResult.get("public_id"),
                "url", uploadResult.get("url"),
                "secureUrl", uploadResult.get("secure_url"),
                "format", uploadResult.getOrDefault("format", ""),
                "bytes", uploadResult.getOrDefault("bytes", 0)
        );

        return ApiResponse.success(response);
    }
}
