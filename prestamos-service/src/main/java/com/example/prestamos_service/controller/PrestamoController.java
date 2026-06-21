package com.example.prestamos_service.controller;

import com.example.prestamos_service.service.PrestamoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {

    private static final Logger log = LoggerFactory.getLogger(PrestamoController.class);

    private final PrestamoService prestamoService;

    @Value("${server.port}")
    private int serverPort;

    public PrestamoController(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @PostMapping("/{libroId}")
    public ResponseEntity<String> solicitarPrestamo(
            @PathVariable UUID libroId,
            @RequestHeader(value = "X-Sede", required = false) String sede,
            @RequestHeader(value = "X-Bibliotecario", required = false) String bibliotecario) {

        String sedeAuditoria = (sede != null && !sede.isBlank()) ? sede : "Sede no declarada";
        String bibliotecarioAuditoria = (bibliotecario != null && !bibliotecario.isBlank())
                ? bibliotecario
                : "Anónimo";

        log.info("Préstamo solicitado | libro={} | bibliotecario={} | sede={} | nodo={}",
                libroId, bibliotecarioAuditoria, sedeAuditoria, serverPort);

        try {
            String resultado = prestamoService.procesarPrestamo(libroId);
            String respuesta = resultado
                    + " | Atendido en nodo " + serverPort
                    + " | Bibliotecario: " + bibliotecarioAuditoria
                    + " | Sede: " + sedeAuditoria;
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(503).body("Fallo de concurrencia o servicio: " + e.getMessage());
        }
    }
}
