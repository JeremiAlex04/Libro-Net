package com.example.prestamos_service.service;

import com.example.prestamos_service.model.Libro;
import com.example.prestamos_service.model.Prestamo;
import com.example.prestamos_service.model.EstadoPrestamo;
import com.example.prestamos_service.repository.LibroRepository;
import com.example.prestamos_service.repository.PrestamoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PrestamoService {
    private static final Logger log = LoggerFactory.getLogger(PrestamoService.class);

    private final LibroRepository libroRepository;
    private final PrestamoRepository prestamoRepository;
    private final RestTemplate restTemplate;
    private final LiderEleccionService liderEleccionService;

    @Value("${reloj.drift-ms:0}")
    private long clockDriftMs;

    public PrestamoService(LibroRepository libroRepository, PrestamoRepository prestamoRepository, RestTemplate restTemplate, LiderEleccionService liderEleccionService) {
        this.libroRepository = libroRepository;
        this.prestamoRepository = prestamoRepository;
        this.restTemplate = restTemplate;
        this.liderEleccionService = liderEleccionService;
    }

    @Transactional
    public String procesarPrestamo(UUID libroId, String sede, String bibliotecario, boolean digital) {
        if (sede == null || sede.isBlank()) {
            throw new IllegalArgumentException("La sede solicitante es obligatoria");
        }

        String sedeNormalizada = sede.trim();
        boolean isNorte;
        if ("Sede Norte".equalsIgnoreCase(sedeNormalizada)) {
            isNorte = true;
        } else if ("Sede Sur".equalsIgnoreCase(sedeNormalizada)) {
            isNorte = false;
        } else {
            throw new IllegalArgumentException("Sede solicitante invalida: " + sedeNormalizada);
        }

        Libro libro = libroRepository.findByIdForUpdate(libroId)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado en el sistema"));

        EstadoPrestamo estadoFinal;
        String mensajeRetorno;

        if (digital) {
            estadoFinal = EstadoPrestamo.ENTREGADO;
            mensajeRetorno = "Préstamo digital aprobado. Copia digital derivada con éxito. Enlace: " + libro.getUrlDigital();
        } else {
            int localStock = isNorte ? libro.getCopiasNorte() : libro.getCopiasSur();
            int otherStock = isNorte ? libro.getCopiasSur() : libro.getCopiasNorte();

            if (localStock > 0) {
                if (isNorte) {
                    libro.setCopiasNorte(libro.getCopiasNorte() - 1);
                } else {
                    libro.setCopiasSur(libro.getCopiasSur() - 1);
                }
                libroRepository.save(libro);
                estadoFinal = EstadoPrestamo.ENTREGADO;
                mensajeRetorno = "Préstamo aprobado de forma local e inmediato. Copias restantes localmente: " + (localStock - 1);
            } else if (otherStock > 0) {
                // Préstamo inter-sede: requiere autorización del líder
                boolean autorizado;
                if (liderEleccionService.isLider()) {
                    autorizado = true;
                    log.info("[LIDERAZGO] Nodo actual es el LÍDER. Autorizando préstamo inter-sede directamente.");
                } else {
                    autorizado = liderEleccionService.solicitarAutorizacionInterSede(libroId, sede);
                }

                if (!autorizado) {
                    throw new RuntimeException("Préstamo inter-sede NO autorizado por el líder. El líder debe aprobar los préstamos que cruzan de sede.");
                }

                if (isNorte) {
                    libro.setCopiasSur(libro.getCopiasSur() - 1);
                } else {
                    libro.setCopiasNorte(libro.getCopiasNorte() - 1);
                }
                libroRepository.save(libro);
                estadoFinal = EstadoPrestamo.PENDIENTE_DE_ENVIO;
                String otraSede = isNorte ? "Sede Sur" : "Sede Norte";
                mensajeRetorno = "Préstamo inter-sede autorizado por el líder. Estado: PENDIENTE DE ENVÍO desde " + otraSede + ". Copias restantes allí: " + (otherStock - 1);
            } else {
                throw new RuntimeException("No hay copias físicas disponibles de este libro en ninguna sede.");
            }
        }

        // Algoritmo de Cristian para sincronización de relojes físicos
        long t0 = System.currentTimeMillis() + clockDriftMs;
        long rtt = 0;
        long tCorregido;

        try {
            // Hacemos la consulta al Servidor de Tiempo en el API Gateway a través de Eureka
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject("http://libronet-api-gateway/api/time", Map.class);
            long t1 = System.currentTimeMillis() + clockDriftMs;

            if (response != null && response.containsKey("serverTimeMs")) {
                long serverTime = ((Number) response.get("serverTimeMs")).longValue();
                rtt = t1 - t0;
                tCorregido = serverTime + (rtt / 2);
                log.info("Sincronización Cristian exitosa | Sede: {} | T0: {} ms | T1: {} ms | T_server: {} ms | RTT: {} ms | T_corregido: {} ms",
                        sede, t0, t1, serverTime, rtt, tCorregido);
            } else {
                throw new RuntimeException("Respuesta nula o incorrecta de la API de Tiempo");
            }
        } catch (Exception e) {
            long t1 = System.currentTimeMillis() + clockDriftMs;
            tCorregido = t1; // Fallback: usar hora local desfasada
            rtt = 0;
            log.warn("Fallo de sincronización Cristian en sede: {} | Error: {} | Fallback a reloj local: {} ms",
                    sede, e.getMessage(), tCorregido);
        }

        LocalDateTime fechaSolicitud = LocalDateTime.ofInstant(Instant.ofEpochMilli(tCorregido), ZoneId.systemDefault());
        LocalDateTime fechaLocalSede = LocalDateTime.ofInstant(Instant.ofEpochMilli(t0), ZoneId.systemDefault());

        // Guardamos el registro de préstamo y logística en la base de datos
        Prestamo prestamo = new Prestamo(
                UUID.randomUUID(),
                libroId,
                libro.getTitulo(),
                bibliotecario,
                sede,
                fechaSolicitud,
                fechaLocalSede,
                clockDriftMs,
                rtt,
                estadoFinal
        );
        prestamo.setAutorizadoPorLider(liderEleccionService.getLiderId());
        prestamoRepository.save(prestamo);

        return mensajeRetorno;
    }

    @Transactional(readOnly = true)
    public List<Prestamo> obtenerTodos() {
        return prestamoRepository.findAll();
    }

    @Transactional
    public Prestamo actualizarEstado(UUID id, EstadoPrestamo estado) {
        UUID idSeguro = Objects.requireNonNull(id, "El id del préstamo es requerido");
        EstadoPrestamo estadoSeguro = Objects.requireNonNull(estado, "El estado es requerido");

        Prestamo prestamo = prestamoRepository.findById(idSeguro)
                .orElseThrow(() -> new RuntimeException("Registro de préstamo no encontrado"));
        prestamo.setEstado(estadoSeguro);
        return prestamoRepository.save(prestamo);
    }
}
