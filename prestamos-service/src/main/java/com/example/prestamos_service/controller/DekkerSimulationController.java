package com.example.prestamos_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/simulacion")
public class DekkerSimulationController {

    // Variables compartidas en memoria (Simulación de la sección crítica)
    private volatile boolean quiereEntrarSedeNorte = false;
    private volatile boolean quiereEntrarSedeSur = false;
    private volatile int turno = 1; // 1 = Norte, 2 = Sur
    private volatile int inventarioSimulado = 1; // El recurso compartido

    @GetMapping("/dekker")
    public ResponseEntity<List<String>> ejecutarDekkerV5() {
        // Reiniciamos variables para cada prueba
        quiereEntrarSedeNorte = false;
        quiereEntrarSedeSur = false;
        turno = 1;
        inventarioSimulado = 1;
        
        List<String> bitacoraEventos = new CopyOnWriteArrayList<>();
        bitacoraEventos.add("[SISTEMA] Iniciando Simulación: Algoritmo de Dekker (Versión 5) para 2 procesos.");

        // Hilo 1: Sede Norte
        Thread sedeNorte = new Thread(() -> {
            bitacoraEventos.add("[SEDE NORTE] Iniciando intento de préstamo...");
            quiereEntrarSedeNorte = true;

            while (quiereEntrarSedeSur) {
                if (turno == 2) {
                    quiereEntrarSedeNorte = false;
                    while (turno == 2) {
                        // Espera activa cediendo el turno (Dekker V5)
                    }
                    quiereEntrarSedeNorte = true;
                }
            }

            // --- SECCIÓN CRÍTICA ---
            bitacoraEventos.add("[SEDE NORTE] Entró a la Sección Crítica (Exclusión Mutua garantizada).");
            if (inventarioSimulado > 0) {
                inventarioSimulado--;
                bitacoraEventos.add("[SEDE NORTE] Préstamo exitoso. Inventario restante: " + inventarioSimulado);
            } else {
                bitacoraEventos.add("[SEDE NORTE] Fallo: Inventario agotado.");
            }
            // --- FIN SECCIÓN CRÍTICA ---

            turno = 2; // Cede el turno a la Sede Sur
            quiereEntrarSedeNorte = false;
            bitacoraEventos.add("[SEDE NORTE] Salió de la Sección Crítica y cedió el turno.");
        });

        // Hilo 2: Sede Sur
        Thread sedeSur = new Thread(() -> {
            bitacoraEventos.add("[SEDE SUR] Iniciando intento de préstamo...");
            quiereEntrarSedeSur = true;

            while (quiereEntrarSedeNorte) {
                if (turno == 1) {
                    quiereEntrarSedeSur = false;
                    while (turno == 1) {
                        // Espera activa cediendo el turno
                    }
                    quiereEntrarSedeSur = true;
                }
            }

            // --- SECCIÓN CRÍTICA ---
            bitacoraEventos.add("[SEDE SUR] Entró a la Sección Crítica (Exclusión Mutua garantizada).");
            if (inventarioSimulado > 0) {
                inventarioSimulado--;
                bitacoraEventos.add("[SEDE SUR] Préstamo exitoso. Inventario restante: " + inventarioSimulado);
            } else {
                bitacoraEventos.add("[SEDE SUR] Fallo: Inventario agotado.");
            }
            // --- FIN SECCIÓN CRÍTICA ---

            turno = 1; // Cede el turno a la Sede Norte
            quiereEntrarSedeSur = false;
            bitacoraEventos.add("[SEDE SUR] Salió de la Sección Crítica y cedió el turno.");
        });

        // Forzamos colisión ejecutando ambos hilos simultáneamente
        sedeNorte.start();
        sedeSur.start();

        try {
            sedeNorte.join();
            sedeSur.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        bitacoraEventos.add("[SISTEMA] Simulación finalizada. Ningún proceso bloqueó al otro de forma permanente.");
        return ResponseEntity.ok(bitacoraEventos);
    }
}