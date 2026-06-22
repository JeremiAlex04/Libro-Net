package com.example.prestamos_service.repository;

import com.example.prestamos_service.model.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PrestamoRepository extends JpaRepository<Prestamo, UUID> {
}
