package com.example.prestamos_service.service;

import com.example.prestamos_service.model.Libro;
import com.example.prestamos_service.repository.LibroRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PrestamoService {
    private final LibroRepository libroRepository;

    public PrestamoService(LibroRepository libroRepository) {
        this.libroRepository = libroRepository;
    }

    @Transactional
    public String procesarPrestamo(UUID libroId) {
        Libro libro = libroRepository.findByIdForUpdate(libroId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado en el sistema"));

        if (libro.getCopiasDisponibles() > 0) {
            libro.setCopiasDisponibles(libro.getCopiasDisponibles() - 1);
            libroRepository.save(libro);
            return "Préstamo exitoso. Copias restantes: " + libro.getCopiasDisponibles();
        } else {
            return "Denegado: No hay copias físicas disponibles de este libro.";
        }
    }

}
