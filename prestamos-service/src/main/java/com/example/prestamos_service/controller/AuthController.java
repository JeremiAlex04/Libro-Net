package com.example.prestamos_service.controller;

import com.example.prestamos_service.model.Bibliotecario;
import com.example.prestamos_service.model.LoginRequest;
import com.example.prestamos_service.model.LoginResponse;
import com.example.prestamos_service.repository.BibliotecarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final BibliotecarioRepository bibliotecarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(BibliotecarioRepository bibliotecarioRepository, PasswordEncoder passwordEncoder) {
        this.bibliotecarioRepository = bibliotecarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        String sede = request.getSede();

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El usuario es requerido");
        }

        if (password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La contraseña es requerida");
        }

        if (sede == null || sede.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La sede es requerida");
        }

        String usernameSeguro = Objects.requireNonNull(username);
        String passwordSeguro = Objects.requireNonNull(password);
        String sedeSegura = Objects.requireNonNull(sede);

        Optional<Bibliotecario> optionalBibliotecario = bibliotecarioRepository.findById(usernameSeguro);

        if (optionalBibliotecario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("El usuario no existe");
        }

        Bibliotecario bibliotecario = optionalBibliotecario.get();
        String passwordAlmacenada = bibliotecario.getPassword();

        if (passwordAlmacenada == null || passwordAlmacenada.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales invalidas");
        }

        boolean credencialesValidas;
        if (isBcryptHash(passwordAlmacenada)) {
            credencialesValidas = passwordEncoder.matches(passwordSeguro, passwordAlmacenada);
        } else {
            // Compatibilidad temporal: migra contraseñas legacy en texto plano a BCrypt al autenticar.
            credencialesValidas = passwordAlmacenada.equals(passwordSeguro);
            if (credencialesValidas) {
                bibliotecario.setPassword(passwordEncoder.encode(passwordSeguro));
                bibliotecarioRepository.save(bibliotecario);
            }
        }

        if (!credencialesValidas) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales invalidas");
        }

        if (!bibliotecario.getSede().equalsIgnoreCase(sedeSegura)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Este bibliotecario no tiene acceso a la sede seleccionada");
        }

        return ResponseEntity.ok(new LoginResponse(
                bibliotecario.getUsername(),
                bibliotecario.getSede(),
                bibliotecario.getRol()
        ));
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
