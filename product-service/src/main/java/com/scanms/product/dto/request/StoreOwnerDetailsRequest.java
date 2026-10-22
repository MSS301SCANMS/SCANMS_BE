package com.scanms.product.dto.request;

import jakarta.validation.constraints.*;
import java.util.Map;

public record StoreOwnerDetailsRequest(
        @NotBlank @Size(max=150) String representativeName,
        @NotBlank @Pattern(regexp="INDIVIDUAL|HOUSEHOLD|ENTERPRISE") String businessType,
        @NotBlank @Size(max=50) String taxCode,
        @NotBlank @Pattern(regexp="^[+]?([0-9().\\-\\s]){8,20}$") String contactPhone,
        @NotBlank @Email @Size(max=254) String contactEmail,
        @Size(max=500) String businessAddress,
        @NotBlank @Size(max=500) String warehouseAddress,
        @Size(max=500) String returnWarehouseAddress) {
    public Map<String,String> toDetails() {
        return Map.of("representativeName", representativeName.trim(), "businessType", businessType,
                "taxCode", taxCode.trim(), "contactPhone", contactPhone.trim(), "contactEmail", contactEmail.trim(),
                "businessAddress", businessAddress == null ? "" : businessAddress.trim(),
                "warehouseAddress", warehouseAddress.trim(), "returnWarehouseAddress",
                returnWarehouseAddress == null ? warehouseAddress.trim() : returnWarehouseAddress.trim());
    }
}
