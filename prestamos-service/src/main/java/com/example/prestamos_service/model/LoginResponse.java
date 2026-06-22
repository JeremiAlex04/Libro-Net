package com.example.prestamos_service.model;

public class LoginResponse {
    private String nombre;
    private String sede;
    private String rol;

    public LoginResponse() {
    }

    public LoginResponse(String nombre, String sede, String rol) {
        this.nombre = nombre;
        this.sede = sede;
        this.rol = rol;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}
