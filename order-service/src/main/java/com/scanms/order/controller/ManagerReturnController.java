package com.scanms.order.controller;

import com.scanms.order.constant.ReturnRequestStatus;
import com.scanms.order.dto.ApiResponse;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/returns/manager")
public class ManagerReturnController {
    private final ManagerStatisticsAccess access;
    private final ShopReturnService service;
    private final ReturnRequestRepository returns;
    public record Decision(@NotNull ReturnRequestStatus status,@NotBlank @Size(min=20,max=3000) String notes) {}
    @GetMapping
    public ApiResponse<List<ShopReturnService.View>> list(@AuthenticationPrincipal Jwt jwt) {
        access.requireManager(jwt);
        return ApiResponse.success(service.managerList());
    }
    @PostMapping("/{id}/decision") @Transactional
    public ApiResponse<Map<String,Object>> decide(@PathVariable String id,@Valid @RequestBody Decision input,@AuthenticationPrincipal Jwt jwt) {
        access.requireManager(jwt);
        if(!Set.of(ReturnRequestStatus.APPROVED,ReturnRequestStatus.REJECTED).contains(input.status()) || input.notes().trim().length()<20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn duyệt hoặc từ chối và nhập lý do ít nhất 20 ký tự.");
        var row=returns.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu đổi trả."));
        if(row.getStatus()!=ReturnRequestStatus.REQUESTED) {
            if(row.getStatus()==input.status() && Objects.equals(row.getDecisionReason(),input.notes().trim()))
                return ApiResponse.success(Map.of("returnRequestId",id,"status",row.getStatus()));
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã được xử lý. Vui lòng tải lại danh sách.");
        }
        if(row.getRefundReference()!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã có tham chiếu hoàn tiền, cần đối soát trước khi xử lý.");
        row.setStatus(input.status()); row.setDecisionReason(input.notes().trim()); row.setResolvedAt(LocalDateTime.now());
        returns.saveAndFlush(row);
        // Approval records eligibility only. The existing finance dispatcher handles refunds separately.
        return ApiResponse.success(Map.of("returnRequestId",id,"status",row.getStatus()));
    }
}
