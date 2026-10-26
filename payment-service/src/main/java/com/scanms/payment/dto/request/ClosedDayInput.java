package com.scanms.payment.dto.request;

import java.time.LocalDate;

public record ClosedDayInput(LocalDate date, String reason) {}
