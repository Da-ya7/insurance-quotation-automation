package com.insurance.quotation;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@SpringBootApplication
@EnableScheduling
public class InsuranceQuotationApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                InsuranceQuotationApplication.class,
                args
        );
    }
}