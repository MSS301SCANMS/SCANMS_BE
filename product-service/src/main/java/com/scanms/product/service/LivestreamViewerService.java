package com.scanms.product.service;
import org.springframework.security.oauth2.jwt.Jwt;
public interface LivestreamViewerService {
    void record(String livestreamId,String visitId,Jwt viewer);
}
