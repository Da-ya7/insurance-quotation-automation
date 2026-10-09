package com.insurance.quotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

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