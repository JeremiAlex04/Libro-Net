package com.example.prestamos_service.controller;

import com.example.prestamos_service.model.Prestamo;
import com.example.prestamos_service.model.EstadoPrestamo;
import com.example.prestamos_service.service.LiderEleccionService;
import com.example.prestamos_service.service.PrestamoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {

    private static final Logger log = LoggerFactory.getLogger(PrestamoController.class);

    private final PrestamoService prestamoService;
    private final LiderEleccionService liderEleccionService;

    @Value("${server.port}")
    private int serverPort;

    public PrestamoController(PrestamoService prestamoService, LiderEleccionService liderEleccionService) {
        this.prestamoService = prestamoService;
        this.liderEleccionService = liderEleccionService;
    }

    @PostMapping("/{libroId}")
    public ResponseEntity<String> solicitarPrestamo(
            @PathVariable UUID libroId,
            @RequestParam(value = "digital", required = false, defaultValue = "false") boolean digital,
            @RequestHeader(value = "X-Sede", required = false) String sede,
            @RequestHeader(value = "X-Bibliotecario", required = false) String bibliotecario) {

        if (liderEleccionService.isOffline()) {
            return ResponseEntity.status(503).body("Nodo caido temporalmente (simulacion de falla). No se pueden procesar prestamos.");
        }

        if (sede == null || sede.isBlank()) {
            return ResponseEntity.badRequest().body("Header X-Sede es requerido.");
        }

        String sedeNormalizada = sede.trim();
        if (!"Sede Norte".equalsIgnoreCase(sedeNormalizada) && !"Sede Sur".equalsIgnoreCase(sedeNormalizada)) {
            return ResponseEntity.badRequest().body("Header X-Sede invalido. Valores permitidos: Sede Norte o Sede Sur.");
        }

        if (bibliotecario == null || bibliotecario.isBlank()) {
            return ResponseEntity.badRequest().body("Header X-Bibliotecario es requerido.");
        }

        String sedeAuditoria = sedeNormalizada;
        String bibliotecarioAuditoria = bibliotecario.trim();

        log.info("Préstamo solicitado | libro={} | digital={} | bibliotecario={} | sede={} | nodo={}",
                libroId, digital, bibliotecarioAuditoria, sedeAuditoria, serverPort);

        try {
            String resultado = prestamoService.procesarPrestamo(libroId, sedeAuditoria, bibliotecarioAuditoria, digital);
            String respuesta = resultado
                    + " | Atendido en nodo " + serverPort
                    + " | Bibliotecario: " + bibliotecarioAuditoria
                    + " | Sede: " + sedeAuditoria;
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Solicitud invalida: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(503).body("Fallo de concurrencia o servicio: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Prestamo>> obtenerTodosLosPrestamos() {
        return ResponseEntity.ok(prestamoService.obtenerTodos());
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<Prestamo> actualizarEstado(
            @PathVariable UUID id,
            @RequestParam EstadoPrestamo estado) {
        log.info("Actualización de estado de logística | ID={} | Nuevo Estado={}", id, estado);
        try {
            Prestamo prestamo = prestamoService.actualizarEstado(id, estado);
            return ResponseEntity.ok(prestamo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
