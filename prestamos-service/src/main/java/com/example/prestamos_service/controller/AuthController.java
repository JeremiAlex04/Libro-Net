package com.example.prestamos_service.controller;

import com.example.prestamos_service.model.Bibliotecario;
import com.example.prestamos_service.model.LoginRequest;
import com.example.prestamos_service.model.LoginResponse;
import com.example.prestamos_service.repository.BibliotecarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final BibliotecarioRepository bibliotecarioRepository;

    public AuthController(BibliotecarioRepository bibliotecarioRepository) {
        this.bibliotecarioRepository = bibliotecarioRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El usuario es requerido");
        }

        Optional<Bibliotecario> optionalBibliotecario = bibliotecarioRepository.findById(request.getUsername());

        if (optionalBibliotecario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("El usuario no existe");
        }

        Bibliotecario bibliotecario = optionalBibliotecario.get();

        if (!bibliotecario.getPassword().equals(request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Contraseña incorrecta");
        }

        if (!bibliotecario.getSede().equalsIgnoreCase(request.getSede())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Este bibliotecario no tiene acceso a la sede seleccionada");
        }

        return ResponseEntity.ok(new LoginResponse(
                bibliotecario.getUsername(),
                bibliotecario.getSede(),
                bibliotecario.getRol()
        ));
    }
}
