package com.example.prestamos_service.service;

import com.example.prestamos_service.model.Bibliotecario;
import com.example.prestamos_service.repository.BibliotecarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BibliotecarioPasswordMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BibliotecarioPasswordMigrationRunner.class);

    private final BibliotecarioRepository bibliotecarioRepository;
    private final PasswordEncoder passwordEncoder;

    public BibliotecarioPasswordMigrationRunner(BibliotecarioRepository bibliotecarioRepository,
                                                PasswordEncoder passwordEncoder) {
        this.bibliotecarioRepository = bibliotecarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        List<Bibliotecario> usuarios = bibliotecarioRepository.findAll();
        int migrados = 0;

        for (Bibliotecario usuario : usuarios) {
            String password = usuario.getPassword();
            if (password == null || password.isBlank()) {
                continue;
            }

            if (isBcryptHash(password)) {
                continue;
            }

            usuario.setPassword(passwordEncoder.encode(password));
            bibliotecarioRepository.save(usuario);
            migrados++;
        }

        if (migrados > 0) {
            log.info("[AUTH] Migracion BCrypt completada. Usuarios actualizados: {}", migrados);
        } else {
            log.info("[AUTH] No se encontraron contraseñas legacy para migrar.");
        }
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }
}
