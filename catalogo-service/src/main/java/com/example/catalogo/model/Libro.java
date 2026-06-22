package com.example.catalogo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Libro {

    @Id
    private UUID id;
    private String titulo;
    private int copiasNorte;
    private int copiasSur;
    private String urlDigital;

    public Libro() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public int getCopiasNorte() {
        return copiasNorte;
    }

    public void setCopiasNorte(int copiasNorte) {
        this.copiasNorte = copiasNorte;
    }

    public int getCopiasSur() {
        return copiasSur;
    }

    public void setCopiasSur(int copiasSur) {
        this.copiasSur = copiasSur;
    }

    public String getUrlDigital() {
        return urlDigital;
    }

    public void setUrlDigital(String urlDigital) {
        this.urlDigital = urlDigital;
    }
}
