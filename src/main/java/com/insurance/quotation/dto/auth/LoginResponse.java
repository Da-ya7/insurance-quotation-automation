package com.insurance.quotation.dto.auth;

public class LoginResponse {

    private final String token;
    private final String role;
    private final String name;

    public LoginResponse(String token, String role, String name) {
        this.token = token;
        this.role = role;
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public String getRole() {
        return role;
    }

    public String getName() {
        return name;
    }
}
