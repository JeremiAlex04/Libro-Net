package com.example.prestamos_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bibliotecario")
public class Bibliotecario {
    @Id
    private String username;
    private String password;
    private String sede;
    private String rol;

    public Bibliotecario() {
    }

    public Bibliotecario(String username, String password, String sede, String rol) {
        this.username = username;
        this.password = password;
        this.sede = sede;
        this.rol = rol;
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

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}
