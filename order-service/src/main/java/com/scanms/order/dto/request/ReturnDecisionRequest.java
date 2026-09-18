package com.scanms.order.dto.request;

import com.scanms.order.constant.ReturnRequestStatus;
import jakarta.validation.constraints.NotNull;

public record ReturnDecisionRequest(@NotNull ReturnRequestStatus status, String decisionReason,
                                    Long refundAmount, String refundReference) {}
