package com.scanms.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scanms.payment.provider")
public record PaymentProviderConfig(String baseUrl, String providerCode, String configReference) {}
