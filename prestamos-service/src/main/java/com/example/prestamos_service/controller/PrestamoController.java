package com.example.prestamos_service.controller;

import com.example.prestamos_service.service.PrestamoService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {
    private final PrestamoService prestamoService;

    public PrestamoController(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @PostMapping("/{libroId}")
    public ResponseEntity<String> solicitarPrestamo(@PathVariable UUID libroId) {
        try {
            String resultado = prestamoService.procesarPrestamo(libroId);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.status(503).body("Fallo de concurrencia o servicio: " + e.getMessage());
        }
    }
}
