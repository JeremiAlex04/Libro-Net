package com.example.catalogo.controller;

import com.example.catalogo.model.Libro;
import com.example.catalogo.repository.LibroRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final LibroRepository repository;

    public CatalogoController(LibroRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/buscar")
    public List<Libro> buscarLibros(@RequestParam String query) {
        return repository.findByTituloContainingIgnoreCase(query);
    }
}
