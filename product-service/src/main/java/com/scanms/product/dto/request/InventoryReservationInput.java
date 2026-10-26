package com.scanms.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record InventoryReservationInput(@NotEmpty @Size(max = 100) List<@Valid InventoryReservationLine> items) {}
