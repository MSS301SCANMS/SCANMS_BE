package com.scanms.order.service;

import com.scanms.order.dto.response.ManagerStatisticsResponse;

import java.time.LocalDate;

public interface ManagerStatisticsService {
    ManagerStatisticsResponse summary(
            String range,
            LocalDate anchor
    );
}
