package com.example.prestamos_service.controller;

import com.example.prestamos_service.service.LiderEleccionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/eleccion")
public class EleccionController {

    private final LiderEleccionService eleccionService;

    public EleccionController(LiderEleccionService eleccionService) {
        this.eleccionService = eleccionService;
    }

    // Interceptor simple para simular caída
    private void checkOnlineStatus() {
        if (eleccionService.isOffline()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Nodo caído temporalmente (Simulación de falla)");
        }
    }

    @GetMapping("/ping")
    public String ping() {
        checkOnlineStatus();
        return "pong";
    }

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> getEstado() {
        // Permitimos leer estado incluso si está offline (para que la consola de control del frontend pueda pintarlo)
        Map<String, Object> estado = new HashMap<>();
        estado.put("nodeId", eleccionService.getNodeId());
        estado.put("nodeName", eleccionService.getNodeName());
        estado.put("liderId", eleccionService.getLiderId());
        estado.put("estadoNode", eleccionService.getEstadoNode());
        estado.put("isOffline", eleccionService.isOffline());

        List<Map<String, Object>> ringInfo = eleccionService.getActiveNodesInRing().stream()
                .map(n -> {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", n.getId());
                    node.put("name", n.getName());
                    node.put("uri", n.getUri());
                    return node;
                })
                .collect(Collectors.toList());
        estado.put("anillo", ringInfo);

        return ResponseEntity.ok(estado);
    }

    @GetMapping("/mensaje/election")
    public ResponseEntity<String> receiveElection(@RequestParam int candidateId) {
        checkOnlineStatus();
        eleccionService.procesarMensajeEleccion(candidateId);
        return ResponseEntity.ok("Mensaje election procesado");
    }

    @GetMapping("/mensaje/coordinator")
    public ResponseEntity<String> receiveCoordinator(@RequestParam int leaderId) {
        checkOnlineStatus();
        eleccionService.procesarMensajeCoordinador(leaderId);
        return ResponseEntity.ok("Mensaje coordinator procesado");
    }

    @PostMapping("/simular-caida")
    public ResponseEntity<String> simularCaida(@RequestParam boolean offline) {
        eleccionService.setOffline(offline);
        return ResponseEntity.ok("Estado de caída cambiado a: " + offline);
    }

    @PostMapping("/forzar-eleccion")
    public ResponseEntity<String> forzarEleccion() {
        checkOnlineStatus();
        eleccionService.iniciarEleccion();
        return ResponseEntity.ok("Elección forzada iniciada");
    }

    // Excepción interna personalizada
    @ResponseStatus(code = HttpStatus.SERVICE_UNAVAILABLE)
    private static class ResponseStatusException extends RuntimeException {
        public ResponseStatusException(HttpStatus status, String message) {
            super(message);
        }
    }
}
