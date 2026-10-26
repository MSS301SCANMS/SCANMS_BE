package com.scanms.promotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PromotionAffiliateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromotionAffiliateServiceApplication.class, args);
    }
}
