package com.scanms.catalog.dto.request;

import com.scanms.catalog.constant.StoreStatus;
import jakarta.validation.constraints.Size;

public record UpdateStoreRequest(@Size(min = 2, max = 150) String name, String description,
                                 StoreStatus approvalStatus) {}
