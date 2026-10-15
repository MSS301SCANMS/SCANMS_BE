package com.scanms.user.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.scanms.user.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final Cloudinary cloudinary;

    @PostMapping("/image")
    public ApiResponse<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "scanms/images") String folder) throws IOException {

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
            @RequestParam(value = "folder", defaultValue = "scanms/videos") String folder) throws IOException {

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
