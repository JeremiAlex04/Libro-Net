package com.example.prestamos_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "prestamo")
public class Prestamo {

    @Id
    private UUID id;

    @Column(name = "libro_id", nullable = false)
    private UUID libroId;

    @Column(name = "libro_titulo", nullable = false)
    private String libroTitulo;

    @Column(nullable = false)
    private String bibliotecario;

    @Column(name = "sede_solicitante", nullable = false)
    private String sedeSolicitante;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_local_sede")
    private LocalDateTime fechaLocalSede;

    @Column(name = "reloj_drift_ms")
    private Long relojDriftMs;

    @Column(name = "reloj_rtt_ms")
    private Long relojRttMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPrestamo estado;

    public Prestamo() {
    }

    public Prestamo(UUID id, UUID libroId, String libroTitulo, String bibliotecario, String sedeSolicitante, LocalDateTime fechaSolicitud, LocalDateTime fechaLocalSede, Long relojDriftMs, Long relojRttMs, EstadoPrestamo estado) {
        this.id = id;
        this.libroId = libroId;
        this.libroTitulo = libroTitulo;
        this.bibliotecario = bibliotecario;
        this.sedeSolicitante = sedeSolicitante;
        this.fechaSolicitud = fechaSolicitud;
        this.fechaLocalSede = fechaLocalSede;
        this.relojDriftMs = relojDriftMs;
        this.relojRttMs = relojRttMs;
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLibroId() {
        return libroId;
    }

    public void setLibroId(UUID libroId) {
        this.libroId = libroId;
    }

    public String getLibroTitulo() {
        return libroTitulo;
    }

    public void setLibroTitulo(String libroTitulo) {
        this.libroTitulo = libroTitulo;
    }

    public String getBibliotecario() {
        return bibliotecario;
    }

    public void setBibliotecario(String bibliotecario) {
        this.bibliotecario = bibliotecario;
    }

    public String getSedeSolicitante() {
        return sedeSolicitante;
    }

    public void setSedeSolicitante(String sedeSolicitante) {
        this.sedeSolicitante = sedeSolicitante;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public LocalDateTime getFechaLocalSede() {
        return fechaLocalSede;
    }

    public void setFechaLocalSede(LocalDateTime fechaLocalSede) {
        this.fechaLocalSede = fechaLocalSede;
    }

    public Long getRelojDriftMs() {
        return relojDriftMs;
    }

    public void setRelojDriftMs(Long relojDriftMs) {
        this.relojDriftMs = relojDriftMs;
    }

    public Long getRelojRttMs() {
        return relojRttMs;
    }

    public void setRelojRttMs(Long relojRttMs) {
        this.relojRttMs = relojRttMs;
    }

    public EstadoPrestamo getEstado() {
        return estado;
    }

    public void setEstado(EstadoPrestamo estado) {
        this.estado = estado;
    }
}
