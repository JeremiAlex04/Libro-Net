package com.example.prestamos_service.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LiderEleccionService {

    private static final Logger log = LoggerFactory.getLogger(LiderEleccionService.class);
    private static final int MAX_EVENTOS = 300;
    private static final int AUTH_MAX_REINTENTOS = 6;
    private static final long AUTH_BACKOFF_INICIAL_MS = 300;
    private static final long AUTH_BACKOFF_MAX_MS = 2000;

    @Value("${eleccion.node-id}")
    private int nodeId;

    @Value("${eleccion.node-name}")
    private String nodeName;

    private int liderId = -1;
    private String estadoNode = "NORMAL"; // NORMAL, ELECTION, RESYNC
    private boolean isOffline = false; // Simulacion de caida
    private long liderazgoEpoca = 0;
    private boolean acceptingRequests = true;
    private long lastClockSyncMs = 0;

    private final DiscoveryClient discoveryClient;
    private final RestTemplate directRestTemplate;
    private final ExecutorService electionExecutor;
    private final List<EventoEleccion> eventos;
    private final Path estadoFilePath;

    public LiderEleccionService(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.directRestTemplate = new RestTemplate();
        this.electionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("lider-eleccion-worker");
            thread.setDaemon(true);
            return thread;
        });
        this.eventos = new CopyOnWriteArrayList<>();
        this.estadoFilePath = Paths.get("state", "eleccion-state.properties");
    }

    @PostConstruct
    public void init() {
        cargarEstadoPersistido();
        registrarEvento("INFO", "Servicio de eleccion inicializado. Estado persistido cargado.");
    }

    @PreDestroy
    public void shutdownExecutor() {
        guardarEstadoPersistido();
        electionExecutor.shutdownNow();
    }

    public int getNodeId() {
        return nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public synchronized int getLiderId() {
        return liderId;
    }

    public synchronized String getEstadoNode() {
        return estadoNode;
    }

    public synchronized boolean isOffline() {
        return isOffline;
    }

    public synchronized long getLiderazgoEpoca() {
        return liderazgoEpoca;
    }

    public synchronized boolean isAcceptingRequests() {
        return !isOffline && acceptingRequests;
    }

    public synchronized long getLastClockSyncMs() {
        return lastClockSyncMs;
    }

    public synchronized List<EventoEleccion> getEventos() {
        return new ArrayList<>(eventos);
    }

    public synchronized void setOffline(boolean offline) {
        this.isOffline = offline;
        if (offline) {
            this.liderId = -1;
            this.estadoNode = "NORMAL";
            this.acceptingRequests = false;
            this.liderazgoEpoca++;
            guardarEstadoPersistido();
            registrarEvento("WARN", "Nodo configurado en OFFLINE (simulacion de caida)");
            log.info("[ELECCIÓN] Nodo {} ({}) configurado como OFFLINE", nodeId, nodeName);
        } else {
            this.acceptingRequests = false;
            this.estadoNode = "RESYNC";
            guardarEstadoPersistido();
            registrarEvento("INFO", "Nodo restaurado a ONLINE. Iniciando resincronizacion previa a aceptar peticiones.");
            log.info("[ELECCIÓN] Nodo {} ({}) restaurado. Se ejecutara resincronizacion.", nodeId, nodeName);
            resincronizarNodo();
        }
    }

    public synchronized boolean resincronizarNodo() {
        if (isOffline) {
            registrarEvento("WARN", "Resincronizacion rechazada: nodo en OFFLINE");
            return false;
        }

        estadoNode = "RESYNC";
        acceptingRequests = false;
        registrarEvento("INFO", "Resincronizacion iniciada");

        sincronizarRelojCristianParaReincorporacion();

        List<NodeInfo> ring = getActiveNodesInRing();
        if (ring.isEmpty()) {
            liderId = nodeId;
            estadoNode = "NORMAL";
            acceptingRequests = true;
            liderazgoEpoca++;
            guardarEstadoPersistido();
            registrarEvento("INFO", "Resincronizacion completada: anillo vacio, nodo se autoasigna liderazgo");
            return true;
        }

        Integer liderDetectado = null;
        long terminoDetectado = liderazgoEpoca;

        for (NodeInfo node : ring) {
            if (node.getId() == nodeId) {
                continue;
            }

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> estadoRemoto = directRestTemplate.getForObject(node.getUri() + "/api/eleccion/estado", Map.class);
                if (estadoRemoto == null) {
                    continue;
                }

                boolean remotoOffline = Boolean.TRUE.equals(estadoRemoto.get("isOffline"));
                long remotoTerm = toLong(estadoRemoto.get("liderazgoEpoca"), 0L);
                int remotoLider = toInt(estadoRemoto.get("liderId"), -1);

                if (remotoTerm > terminoDetectado) {
                    terminoDetectado = remotoTerm;
                }

                if (!remotoOffline && remotoLider > 0) {
                    liderDetectado = remotoLider;
                }
            } catch (Exception e) {
                registrarEvento("WARN", "Resincronizacion: no se pudo consultar estado remoto de " + node.getName() + " - " + e.getMessage());
            }
        }

        liderazgoEpoca = Math.max(liderazgoEpoca, terminoDetectado);

        final Integer liderDetectadoFinal = liderDetectado;
        boolean liderVigenteEnRing = liderDetectadoFinal != null
            && ring.stream().anyMatch(n -> n.getId() == liderDetectadoFinal);

        if (liderVigenteEnRing) {
            liderId = liderDetectado;
            estadoNode = "NORMAL";
            acceptingRequests = true;
            guardarEstadoPersistido();
            registrarEvento("INFO", "Resincronizacion completada. Lider reconocido: " + liderId);
            return true;
        }

        registrarEvento("INFO", "No hay lider vigente durante resincronizacion. Iniciando nueva eleccion.");
        iniciarEleccion();

        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (liderId != -1) {
                estadoNode = "NORMAL";
                acceptingRequests = true;
                guardarEstadoPersistido();
                registrarEvento("INFO", "Resincronizacion completada tras eleccion. Lider: " + liderId);
                return true;
            }
            sleepSilently(250);
        }

        estadoNode = "NORMAL";
        acceptingRequests = false;
        guardarEstadoPersistido();
        registrarEvento("ERROR", "Resincronizacion incompleta: no se definio lider dentro del timeout");
        return false;
    }

    // Estructura para almacenar info resumida de cada nodo en el anillo
    public static class NodeInfo {
        private final int id;
        private final String name;
        private final String uri;

        public NodeInfo(int id, String name, String uri) {
            this.id = id;
            this.name = name;
            this.uri = uri;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getUri() { return uri; }
    }

    public static class EventoEleccion {
        private final String timestamp;
        private final String nivel;
        private final String nodo;
        private final String mensaje;

        public EventoEleccion(String timestamp, String nivel, String nodo, String mensaje) {
            this.timestamp = timestamp;
            this.nivel = nivel;
            this.nodo = nodo;
            this.mensaje = mensaje;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getNivel() {
            return nivel;
        }

        public String getNodo() {
            return nodo;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    // Obtener los nodos activos en el anillo ordenados por ID ascendente
    public List<NodeInfo> getActiveNodesInRing() {
        List<ServiceInstance> instances = discoveryClient.getInstances("libronet-prestamos");
        List<NodeInfo> nodes = new ArrayList<>();

        for (ServiceInstance instance : instances) {
            String nodeIdStr = instance.getMetadata().get("nodeId");
            String name = instance.getMetadata().get("sede");
            if (nodeIdStr != null) {
                try {
                    int id = Integer.parseInt(nodeIdStr);
                    String uri = instance.getUri().toString();
                    nodes.add(new NodeInfo(id, name != null ? name : "Nodo " + id, uri));
                } catch (NumberFormatException e) {
                    log.error("Error parseando nodeId de metadata: {}", nodeIdStr);
                }
            }
        }

        nodes.sort(Comparator.comparingInt(NodeInfo::getId));
        return nodes;
    }

    // Iniciar la eleccion
    public synchronized void iniciarEleccion() {
        if (isOffline) {
            registrarEvento("WARN", "No se inicia eleccion: nodo OFFLINE");
            return;
        }

        this.liderazgoEpoca++;
        this.estadoNode = "ELECTION";
        this.liderId = -1;
        this.acceptingRequests = false;
        guardarEstadoPersistido();

        registrarEvento("INFO", "Eleccion iniciada. term=" + liderazgoEpoca);
        log.info("[ELECCIÓN] Nodo {} ({}) inicia elección, term={}", nodeId, nodeName, liderazgoEpoca);

        enviarMensajeEleccion(nodeId, this.liderazgoEpoca);
    }

    private void enviarMensajeEleccion(int candidateId, long term) {
        List<NodeInfo> ring = getActiveNodesInRing();
        if (ring.isEmpty()) {
            registrarEvento("WARN", "Anillo vacio durante eleccion; auto-liderazgo");
            declararLider(term);
            return;
        }

        int myIndex = -1;
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i).getId() == this.nodeId) {
                myIndex = i;
                break;
            }
        }

        if (myIndex == -1) {
            registrarEvento("ERROR", "Nodo no encontrado en Eureka durante eleccion; fallback a auto-liderazgo");
            declararLider(term);
            return;
        }

        for (int i = 1; i <= ring.size(); i++) {
            int nextIndex = (myIndex + i) % ring.size();
            NodeInfo target = ring.get(nextIndex);

            if (target.getId() == this.nodeId) {
                registrarEvento("WARN", "No hay sucesores disponibles; auto-liderazgo");
                declararLider(term);
                return;
            }

            try {
                String targetUrl = target.getUri() + "/api/eleccion/mensaje/election?candidateId=" + candidateId + "&term=" + term;
                registrarEvento("INFO", "Enviando ELECTION(" + candidateId + ", term=" + term + ") a " + target.getName());
                enviarAsync(targetUrl, target.getName(), "ELECTION");
                return;
            } catch (Exception e) {
                registrarEvento("WARN", "Fallo al enviar ELECTION a " + target.getName() + ": " + e.getMessage());
            }
        }

        declararLider(term);
    }

    public synchronized void procesarMensajeEleccion(int candidateId, long term) {
        if (isOffline) {
            throw new RuntimeException("Nodo caído");
        }

        if (term < this.liderazgoEpoca) {
            registrarEvento("INFO", "ELECTION obsoleto ignorado. term=" + term + ", termActual=" + liderazgoEpoca);
            return;
        }

        if (term > this.liderazgoEpoca) {
            this.liderazgoEpoca = term;
            this.estadoNode = "ELECTION";
            this.liderId = -1;
            this.acceptingRequests = false;
            guardarEstadoPersistido();
        }

        registrarEvento("INFO", "Recibido ELECTION(" + candidateId + ", term=" + term + ")");

        if (candidateId > this.nodeId) {
            this.estadoNode = "ELECTION";
            this.liderId = -1;
            this.acceptingRequests = false;
            guardarEstadoPersistido();
            enviarMensajeEleccion(candidateId, term);
        } else if (candidateId < this.nodeId) {
            if ("NORMAL".equals(this.estadoNode)) {
                this.estadoNode = "ELECTION";
                this.liderId = -1;
                this.acceptingRequests = false;
                guardarEstadoPersistido();
                enviarMensajeEleccion(this.nodeId, term);
            }
        } else {
            registrarEvento("INFO", "Mensaje ELECTION retorno al emisor. Nodo actual gana.");
            declararLider(term);
        }
    }

    private void declararLider(long term) {
        if (term < this.liderazgoEpoca) {
            registrarEvento("INFO", "Declaracion de lider omitida por term obsoleto");
            return;
        }

        this.liderazgoEpoca = term;
        this.liderId = this.nodeId;
        this.estadoNode = "NORMAL";
        this.acceptingRequests = true;
        guardarEstadoPersistido();

        registrarEvento("INFO", "Nodo declarado lider. term=" + term);
        log.info("[ELECCIÓN] NODO {} ({}) ES LÍDER (term={})", nodeId, nodeName, term);
        enviarMensajeCoordinador(this.nodeId, term);
    }

    private void enviarMensajeCoordinador(int leaderId, long term) {
        List<NodeInfo> ring = getActiveNodesInRing();
        if (ring.size() <= 1) {
            return;
        }

        int myIndex = -1;
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i).getId() == this.nodeId) {
                myIndex = i;
                break;
            }
        }

        if (myIndex == -1) {
            return;
        }

        for (int i = 1; i <= ring.size(); i++) {
            int nextIndex = (myIndex + i) % ring.size();
            NodeInfo target = ring.get(nextIndex);

            if (target.getId() == this.nodeId) {
                registrarEvento("INFO", "COORDINATOR completo la vuelta del anillo");
                return;
            }

            try {
                String targetUrl = target.getUri() + "/api/eleccion/mensaje/coordinator?leaderId=" + leaderId + "&term=" + term;
                registrarEvento("INFO", "Enviando COORDINATOR(" + leaderId + ", term=" + term + ") a " + target.getName());
                enviarAsync(targetUrl, target.getName(), "COORDINATOR");
                return;
            } catch (Exception e) {
                registrarEvento("WARN", "Fallo envio COORDINATOR a " + target.getName() + ": " + e.getMessage());
            }
        }
    }

    private void enviarAsync(String targetUrl, String targetName, String tipoMensaje) {
        electionExecutor.execute(() -> {
            try {
                directRestTemplate.getForObject(targetUrl, String.class);
            } catch (Exception e) {
                registrarEvento("WARN", "Fallo envio asíncrono de " + tipoMensaje + " a " + targetName + ": " + e.getMessage());
                log.warn("[ELECCIÓN] Falló envío asíncrono de {} a {}: {}", tipoMensaje, targetName, e.getMessage());
            }
        });
    }

    public synchronized void procesarMensajeCoordinador(int leaderId, long term) {
        if (isOffline) {
            throw new RuntimeException("Nodo caído");
        }

        if (term < this.liderazgoEpoca) {
            registrarEvento("INFO", "COORDINATOR obsoleto ignorado. term=" + term + ", termActual=" + liderazgoEpoca);
            return;
        }

        this.liderazgoEpoca = term;
        this.liderId = leaderId;
        this.estadoNode = "NORMAL";
        this.acceptingRequests = true;
        guardarEstadoPersistido();

        registrarEvento("INFO", "Recibido COORDINATOR. Nuevo lider=" + leaderId + ", term=" + term);

        if (leaderId == this.nodeId) {
            return;
        }

        enviarMensajeCoordinador(leaderId, term);
    }

    @Scheduled(fixedRate = 5000)
    public void verificarLider() {
        if (isOffline) {
            return;
        }

        if (!acceptingRequests && !"ELECTION".equals(estadoNode)) {
            return;
        }

        if (this.liderId == -1) {
            if ("NORMAL".equals(this.estadoNode)) {
                registrarEvento("INFO", "Monitor detecta ausencia de lider. Iniciando eleccion.");
                iniciarEleccion();
            }
            return;
        }

        if (this.liderId == this.nodeId) {
            List<NodeInfo> ring = getActiveNodesInRing();
            boolean haySuperior = ring.stream().anyMatch(n -> n.getId() > this.nodeId);
            if (haySuperior) {
                registrarEvento("WARN", "Nodo lider detecta IDs superiores activos. Re-eleccion preventiva.");
                this.liderId = -1;
                iniciarEleccion();
            }
            return;
        }

        List<NodeInfo> ring = getActiveNodesInRing();
        Optional<NodeInfo> leaderNode = ring.stream().filter(n -> n.getId() == this.liderId).findFirst();

        if (!leaderNode.isPresent()) {
            registrarEvento("WARN", "Lider no encontrado en Eureka. Se inicia eleccion.");
            iniciarEleccion();
            return;
        }

        try {
            String pingUrl = leaderNode.get().getUri() + "/api/eleccion/ping";
            String response = directRestTemplate.getForObject(pingUrl, String.class);
            if (!"pong".equalsIgnoreCase(response)) {
                throw new RuntimeException("Respuesta de ping incorrecta");
            }
        } catch (Exception e) {
            registrarEvento("WARN", "Lider inalcanzable. Se inicia eleccion. Detalle: " + e.getMessage());
            iniciarEleccion();
        }
    }

    public boolean isLider() {
        return this.nodeId == this.liderId && this.liderId != -1;
    }

    private Optional<NodeInfo> obtenerNodoLider() {
        return getActiveNodesInRing().stream()
                .filter(n -> n.getId() == this.liderId)
                .findFirst();
    }

    // Lado del líder: autoriza (o deniega) un préstamo inter-sede
    public synchronized boolean autorizarPrestamoInterSede(UUID libroId, String sedeSolicitante) {
        if (isOffline) {
            throw new RuntimeException("Nodo caído");
        }
        if (!isLider()) {
            registrarEvento("WARN", "Intento de autorizacion en nodo no-lider");
            return false;
        }

        registrarEvento("INFO", "Lider autoriza prestamo inter-sede libro=" + libroId + " sedeSolicitante=" + sedeSolicitante);
        return true;
    }

    // Lado del solicitante: pide autorización al líder actual con retry + backoff.
    public boolean solicitarAutorizacionInterSede(UUID libroId, String sedeSolicitante) {
        long backoff = AUTH_BACKOFF_INICIAL_MS;

        for (int intento = 1; intento <= AUTH_MAX_REINTENTOS; intento++) {
            if (isOffline) {
                registrarEvento("WARN", "Autorizacion inter-sede cancelada: nodo en OFFLINE");
                return false;
            }

            Optional<NodeInfo> liderOpt = obtenerNodoLider();
            if (!liderOpt.isPresent()) {
                registrarEvento("WARN", "Intento " + intento + ": sin lider disponible. Disparando eleccion y reintentando.");
                iniciarEleccion();
                sleepSilently(backoff);
                backoff = Math.min(backoff * 2, AUTH_BACKOFF_MAX_MS);
                continue;
            }

            NodeInfo lider = liderOpt.get();
            String url = lider.getUri()
                    + "/api/eleccion/autorizar-prestamo-inter-sede"
                    + "?libroId=" + libroId
                    + "&sedeSolicitante=" + URLEncoder.encode(sedeSolicitante, StandardCharsets.UTF_8);

            try {
                registrarEvento("INFO", "Intento " + intento + ": solicitando autorizacion al lider " + lider.getName());
                @SuppressWarnings("unchecked")
                Map<String, Object> response = directRestTemplate.getForObject(url, Map.class);
                boolean autorizado = response != null && Boolean.TRUE.equals(response.get("autorizado"));
                if (autorizado) {
                    registrarEvento("INFO", "Autorizacion inter-sede confirmada en intento " + intento);
                    return true;
                }

                registrarEvento("WARN", "Intento " + intento + ": lider respondio no autorizado");
            } catch (Exception e) {
                registrarEvento("WARN", "Intento " + intento + ": error al contactar lider - " + e.getMessage());
                synchronized (this) {
                    if (liderId == lider.getId()) {
                        liderId = -1;
                        acceptingRequests = false;
                    }
                }
                iniciarEleccion();
            }

            sleepSilently(backoff);
            backoff = Math.min(backoff * 2, AUTH_BACKOFF_MAX_MS);
        }

        registrarEvento("ERROR", "Autorizacion inter-sede agotada tras " + AUTH_MAX_REINTENTOS + " intentos. Se fuerza rollback transaccional.");
        return false;
    }

    private synchronized void registrarEvento(String nivel, String mensaje) {
        EventoEleccion evento = new EventoEleccion(Instant.now().toString(), nivel, nodeName + "(" + nodeId + ")", mensaje);
        eventos.add(evento);
        if (eventos.size() > MAX_EVENTOS) {
            eventos.remove(0);
        }
    }

    private synchronized void guardarEstadoPersistido() {
        try {
            Files.createDirectories(estadoFilePath.getParent());
            Properties p = new Properties();
            p.setProperty("liderId", String.valueOf(liderId));
            p.setProperty("liderazgoEpoca", String.valueOf(liderazgoEpoca));
            p.setProperty("estadoNode", estadoNode);
            p.setProperty("acceptingRequests", String.valueOf(acceptingRequests));
            p.setProperty("lastClockSyncMs", String.valueOf(lastClockSyncMs));
            try (OutputStream os = Files.newOutputStream(estadoFilePath)) {
                p.store(os, "LibroNet - Estado de eleccion persistido");
            }
        } catch (IOException e) {
            log.warn("[ELECCIÓN] No se pudo persistir estado de elección: {}", e.getMessage());
        }
    }

    private synchronized void cargarEstadoPersistido() {
        if (!Files.exists(estadoFilePath)) {
            return;
        }
        try (InputStream is = Files.newInputStream(estadoFilePath)) {
            Properties p = new Properties();
            p.load(is);
            liderId = toInt(p.getProperty("liderId"), -1);
            liderazgoEpoca = toLong(p.getProperty("liderazgoEpoca"), 0L);
            estadoNode = p.getProperty("estadoNode", "NORMAL");
            acceptingRequests = Boolean.parseBoolean(p.getProperty("acceptingRequests", "true"));
            lastClockSyncMs = toLong(p.getProperty("lastClockSyncMs"), 0L);
        } catch (IOException e) {
            log.warn("[ELECCIÓN] No se pudo cargar estado persistido: {}", e.getMessage());
        }
    }

    private void sincronizarRelojCristianParaReincorporacion() {
        List<ServiceInstance> gateways = discoveryClient.getInstances("libronet-api-gateway");
        if (gateways.isEmpty()) {
            registrarEvento("WARN", "Resync de reloj omitida: no hay instancias de gateway disponibles");
            return;
        }

        ServiceInstance gateway = gateways.get(0);
        String url = gateway.getUri() + "/api/time";

        long t0 = System.currentTimeMillis();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = directRestTemplate.getForObject(url, Map.class);
            long t1 = System.currentTimeMillis();
            if (response == null || !response.containsKey("serverTimeMs")) {
                throw new IllegalStateException("Respuesta sin serverTimeMs");
            }

            long serverTimeMs = ((Number) response.get("serverTimeMs")).longValue();
            long rtt = t1 - t0;
            synchronized (this) {
                this.lastClockSyncMs = serverTimeMs + (rtt / 2);
                guardarEstadoPersistido();
            }
            registrarEvento("INFO", "Resync de reloj completada via Cristian. RTT=" + rtt + "ms");
        } catch (Exception e) {
            registrarEvento("WARN", "Resync de reloj fallida: " + e.getMessage());
        }
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
