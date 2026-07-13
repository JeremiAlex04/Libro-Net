package com.example.catalogo.controller;

import com.example.catalogo.model.Libro;
import com.example.catalogo.repository.LibroRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final LibroRepository repository;

    @Value("${server.port}")
    private int serverPort;

    public CatalogoController(LibroRepository repository) {
        this.repository = repository;
    }

    private String instanceLabel() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + serverPort;
        } catch (Exception e) {
            return "catalogo:" + serverPort;
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Libro>> buscarLibros(@RequestParam String query) {
        return ResponseEntity.ok()
                .header("X-LibroNet-Instance", instanceLabel())
                .body(repository.findByTituloContainingIgnoreCase(query));
    }

    @GetMapping("/instancia")
    public ResponseEntity<Map<String, String>> instancia() {
        return ResponseEntity.ok(Map.of(
                "service", "libronet-catalogo",
                "instance", instanceLabel()
        ));
    }
}
