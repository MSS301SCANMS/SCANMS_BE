package com.scanms.promotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class PromotionAffiliateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromotionAffiliateServiceApplication.class, args);
    }
}
