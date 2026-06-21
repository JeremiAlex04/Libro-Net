package com.example.catalogo.repository;

import com.example.catalogo.model.Libro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LibroRepository extends JpaRepository<Libro, UUID> {
    List<Libro> findByTituloContainingIgnoreCase(String titulo);
}
