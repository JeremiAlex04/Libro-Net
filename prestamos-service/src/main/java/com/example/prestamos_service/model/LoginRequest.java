package com.example.prestamos_service.model;

public class LoginRequest {
    private String username;
    private String password;
    private String sede;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password, String sede) {
        this.username = username;
        this.password = password;
        this.sede = sede;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }
}
