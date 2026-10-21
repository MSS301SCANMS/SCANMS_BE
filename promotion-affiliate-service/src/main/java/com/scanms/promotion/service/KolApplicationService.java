package com.scanms.promotion.service;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.entity.CollaboratorProfile;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.mapper.CollaboratorProfileMapper;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class KolApplicationService {
    private final CollaboratorProfileRepository repository;
    private final CollaboratorProfileMapper mapper;
    private final VoucherAccess access;
    @Transactional
    public CollaboratorProfileResponse submit(Map<String,Object> input) {
        String userId=access.userId();
        validate(input);
        var profile=repository.lockByUserId(userId).orElseGet(()->CollaboratorProfile.builder().userId(userId).policyVersion("v1").build());
        if(profile.getApprovalStatus()==CollaboratorStatus.APPROVED || profile.getApprovalStatus()==CollaboratorStatus.SUSPENDED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Hồ sơ đã được xét duyệt, không thể nộp lại tại đây.");
        var data=new LinkedHashMap<String,Object>();
        for(String key:List.of("idCardNumber","taxCode","bankName","bankAccountNumber","bankAccountName","bio","frontCardUrl","backCardUrl","platform","channelName","channelUrl","channelProofUrl"))
            if(input.get(key) instanceof String value) data.put(key,value.trim());
        data.put("followerCount",((Number)input.get("followerCount")).longValue());
        data.put("submittedAt",LocalDateTime.now().toString());
        profile.setApplicationData(data); profile.setApprovalStatus(CollaboratorStatus.PENDING); profile.setRejectionReason(null);
        if(profile.getJoinedAt()==null)profile.setJoinedAt(LocalDateTime.now());
        return mapper.toResponse(repository.saveAndFlush(profile));
    }
    public static void validate(Map<String,Object> data) {
        if(data==null)throw invalid("Hồ sơ chưa có thông tin chi tiết. Vui lòng bổ sung trước khi duyệt.");
        for(String key:List.of("idCardNumber","bankName","bankAccountNumber","bankAccountName","frontCardUrl","backCardUrl","platform","channelName","channelUrl"))
            if(!(data.get(key) instanceof String value) || value.isBlank() || value.length()>4096)throw invalid("Hồ sơ còn thiếu hoặc chưa hợp lệ: "+key);
        if(!data.get("idCardNumber").toString().matches("[0-9]{9,12}"))throw invalid("Số CCCD phải gồm 9 đến 12 chữ số.");
        if(!Set.of("TIKTOK","FACEBOOK","YOUTUBE","INSTAGRAM","SHOPEE_VIDEO","LEMON8","OTHER").contains(data.get("platform")))throw invalid("Nền tảng kênh chưa hợp lệ.");
        if(!(data.get("followerCount") instanceof Number count) || !Double.isFinite(count.doubleValue()) || count.doubleValue()<0 || count.doubleValue()!=Math.floor(count.doubleValue()) || count.doubleValue()>1_000_000_000_000L)throw invalid("Số người theo dõi chưa hợp lệ.");
        for(String key:List.of("channelUrl","frontCardUrl","backCardUrl","channelProofUrl")) {
            if(data.get(key)==null || data.get(key).toString().isBlank())continue;
            try { var uri=URI.create(data.get(key).toString());if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)throw new IllegalArgumentException(); }
            catch(Exception ex){throw invalid("Đường dẫn chưa hợp lệ: "+key);}
        }
        for(Object value:data.values()) if(value instanceof String text && text.length()>4096)throw invalid("Một trường thông tin vượt quá độ dài cho phép.");
    }
    private static ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
