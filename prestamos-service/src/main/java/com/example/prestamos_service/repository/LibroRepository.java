package com.example.prestamos_service.repository;
import com.example.prestamos_service.model.Libro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;


public interface LibroRepository extends JpaRepository<Libro, UUID> {
    //El nucleo de la consistencia fuerte
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Libro l WHERE l.id = :id")
    Optional<Libro> findByIdForUpdate(@Param("id") UUID id);
}
