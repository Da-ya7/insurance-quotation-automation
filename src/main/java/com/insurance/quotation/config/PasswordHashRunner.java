package com.insurance.quotation.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHashRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String password = "Admin@123";

        String hash = encoder.encode(password);

        System.out.println("=================================");
        System.out.println("BCrypt Hash:");
        System.out.println(hash);
        System.out.println("=================================");
    }
}