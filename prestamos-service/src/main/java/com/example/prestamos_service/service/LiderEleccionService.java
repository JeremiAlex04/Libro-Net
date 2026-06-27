package com.example.prestamos_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PreDestroy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LiderEleccionService {

    private static final Logger log = LoggerFactory.getLogger(LiderEleccionService.class);

    @Value("${eleccion.node-id}")
    private int nodeId;

    @Value("${eleccion.node-name}")
    private String nodeName;

    private int liderId = -1;
    private String estadoNode = "NORMAL"; // NORMAL, ELECTION
    private boolean isOffline = false;   // Para simular caída
    private long liderazgoEpoca = 0;

    private final DiscoveryClient discoveryClient;
    private final RestTemplate directRestTemplate; // RestTemplate sin @LoadBalanced para llamadas directas
    private final ExecutorService electionExecutor;

    public LiderEleccionService(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.directRestTemplate = new RestTemplate(); // Cliente directo para IPs/Puertos específicos
        this.electionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("lider-eleccion-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdownExecutor() {
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

    public synchronized void setOffline(boolean offline) {
        this.isOffline = offline;
        if (offline) {
            this.liderId = -1;
            this.estadoNode = "NORMAL";
            this.liderazgoEpoca++;
            log.info("[ELECCIÓN] Nodo {} ({}) configurado como OFFLINE (Simulación de caída)", nodeId, nodeName);
        } else {
            log.info("[ELECCIÓN] Nodo {} ({}) configurado como ONLINE (Restaurado)", nodeId, nodeName);
        }
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
                    // Usamos la URI provista por Eureka
                    String uri = instance.getUri().toString();
                    nodes.add(new NodeInfo(id, name != null ? name : "Nodo " + id, uri));
                } catch (NumberFormatException e) {
                    log.error("Error parseando nodeId de metadata: {}", nodeIdStr);
                }
            }
        }

        // Ordenamos por ID de nodo ascendente
        nodes.sort(Comparator.comparingInt((NodeInfo n) -> n.getId()));
        return nodes;
    }

    // Iniciar la elección
    public synchronized void iniciarEleccion() {
        if (isOffline) {
            log.warn("[ELECCIÓN] No se puede iniciar elección en {} porque el nodo está simulado como caído", nodeName);
            return;
        }

        this.liderazgoEpoca++;
        log.info("[ELECCIÓN] Nodo {} ({}) ha iniciado un proceso de elección.", nodeId, nodeName);
        this.estadoNode = "ELECTION";
        this.liderId = -1;

        enviarMensajeEleccion(nodeId, this.liderazgoEpoca);
    }

    // Enviar el mensaje ELECTION(candidateId) al sucesor en el anillo
    private void enviarMensajeEleccion(int candidateId, long term) {
        List<NodeInfo> ring = getActiveNodesInRing();
        if (ring.isEmpty()) {
            log.warn("[ELECCIÓN] Anillo vacío. Declarando a sí mismo como líder.");
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
            log.error("[ELECCIÓN] Este nodo {} no se encuentra registrado en Eureka.", nodeId);
            // Caída temporal o falta de registro en Eureka. Declarar líder directo como fallback
            declararLider(term);
            return;
        }

        // Intentamos enviar al sucesor. Si falla, saltamos al siguiente
        for (int i = 1; i <= ring.size(); i++) {
            int nextIndex = (myIndex + i) % ring.size();
            NodeInfo target = ring.get(nextIndex);

            if (target.getId() == this.nodeId) {
                // Hemos dado la vuelta completa y todos los demás están caídos
                log.info("[ELECCIÓN] Todos los sucesores fallaron o anillo de tamaño 1. Declarando a sí mismo como líder.");
                declararLider(term);
                return;
            }

            try {
                String targetUrl = target.getUri() + "/api/eleccion/mensaje/election?candidateId=" + candidateId + "&term=" + term;
                log.info("[ELECCIÓN] Enviando mensaje ELECTION({}, term={}) al sucesor {} en {}", candidateId, term, target.getName(), targetUrl);
                enviarAsync(targetUrl, target.getName(), "ELECTION");
                return;
            } catch (Exception e) {
                log.warn("[ELECCIÓN] Falló el envío al sucesor {} ({}). Intentando saltar al siguiente nodo.", target.getName(), e.getMessage());
            }
        }

        declararLider(term);
    }

    // Procesar mensaje ELECTION recibido
    public synchronized void procesarMensajeEleccion(int candidateId, long term) {
        if (isOffline) {
            throw new RuntimeException("Nodo caído");
        }

        if (term < this.liderazgoEpoca) {
            log.info("[ELECCIÓN] Ignorando ELECTION({}, term={}) por ser obsoleto. term actual={}", candidateId, term, liderazgoEpoca);
            return;
        }

        if (term > this.liderazgoEpoca) {
            this.liderazgoEpoca = term;
            this.estadoNode = "ELECTION";
            this.liderId = -1;
        }

        log.info("[ELECCIÓN] Nodo {} ({}) recibió ELECTION({}, term={}). Mi ID: {}", nodeId, nodeName, candidateId, term, nodeId);

        if (candidateId > this.nodeId) {
            this.estadoNode = "ELECTION";
            this.liderId = -1;
            enviarMensajeEleccion(candidateId, term);
        } else if (candidateId < this.nodeId) {
            if ("NORMAL".equals(this.estadoNode)) {
                this.estadoNode = "ELECTION";
                this.liderId = -1;
                enviarMensajeEleccion(this.nodeId, term);
            } else {
                log.info("[ELECCIÓN] Nodo {} ya está en estado ELECTION con ID superior o igual, ignorando ELECTION({})", nodeId, candidateId);
            }
        } else {
            // candidateId == nodeId: ¡Ganamos la elección!
            log.info("[ELECCIÓN] ¡El mensaje ELECTION regresó a mí! Nodo {} ({}) es el nuevo líder.", nodeId, nodeName);
            declararLider(term);
        }
    }

    // Declararse a sí mismo líder y enviar coordinador
    private void declararLider(long term) {
        if (term < this.liderazgoEpoca) {
            log.info("[ELECCIÓN] Se omite declaración de líder por term obsoleto: {} < {}", term, liderazgoEpoca);
            return;
        }

        this.liderazgoEpoca = term;
        this.liderId = this.nodeId;
        this.estadoNode = "NORMAL";
        log.info("[ELECCIÓN] *** NODO {} ({}) SE HA DECLARADO LÍDER DEL ANILLO (term={}) ***", nodeId, nodeName, term);
        enviarMensajeCoordinador(this.nodeId, term);
    }

    // Enviar mensaje COORDINATOR(leaderId) al sucesor
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

        if (myIndex == -1) return;

        for (int i = 1; i <= ring.size(); i++) {
            int nextIndex = (myIndex + i) % ring.size();
            NodeInfo target = ring.get(nextIndex);

            if (target.getId() == this.nodeId) {
                // Regresó a nosotros, fin del mensaje coordinador
                log.info("[ELECCIÓN] Mensaje COORDINATOR({}, term={}) dio la vuelta completa en el anillo.", leaderId, term);
                return;
            }

            try {
                String targetUrl = target.getUri() + "/api/eleccion/mensaje/coordinator?leaderId=" + leaderId + "&term=" + term;
                log.info("[ELECCIÓN] Enviando mensaje COORDINATOR({}, term={}) al sucesor {} en {}", leaderId, term, target.getName(), targetUrl);
                enviarAsync(targetUrl, target.getName(), "COORDINATOR");
                return;
            } catch (Exception e) {
                log.warn("[ELECCIÓN] Falló envío de COORDINATOR al sucesor {} ({}). Intentando con el siguiente.", target.getName(), e.getMessage());
            }
        }
    }

    private void enviarAsync(String targetUrl, String targetName, String tipoMensaje) {
        electionExecutor.execute(() -> {
            try {
                directRestTemplate.getForObject(targetUrl, String.class);
            } catch (Exception e) {
                log.warn("[ELECCIÓN] Falló envío asíncrono de {} a {}: {}", tipoMensaje, targetName, e.getMessage());
            }
        });
    }

    // Procesar mensaje COORDINATOR recibido
    public synchronized void procesarMensajeCoordinador(int leaderId, long term) {
        if (isOffline) {
            throw new RuntimeException("Nodo caído");
        }

        if (term < this.liderazgoEpoca) {
            log.info("[ELECCIÓN] Ignorando COORDINATOR({}, term={}) por ser obsoleto. term actual={}", leaderId, term, liderazgoEpoca);
            return;
        }

        this.liderazgoEpoca = term;
        log.info("[ELECCIÓN] Nodo {} ({}) recibió COORDINATOR({}, term={}).", nodeId, nodeName, leaderId, term);

        if (leaderId == this.nodeId) {
            log.info("[ELECCIÓN] Fin de la transmisión del coordinador. Líder establecido: {}", leaderId);
            this.liderId = leaderId;
            this.estadoNode = "NORMAL";
            return;
        }

        this.liderId = leaderId;
        this.estadoNode = "NORMAL";

        // Reenviar
        enviarMensajeCoordinador(leaderId, term);
    }

    // Heartbeat periódico ejecutado cada 5 segundos
    @Scheduled(fixedRate = 5000)
    public void verificarLider() {
        if (isOffline) {
            return;
        }

        // Si no hay líder conocido o está en elección, iniciamos elección
        if (this.liderId == -1) {
            if ("NORMAL".equals(this.estadoNode)) {
                log.info("[MONITOR] Sin líder establecido. Iniciando elección desde {}...", nodeName);
                iniciarEleccion();
            }
            return;
        }

        // Si somos el líder, verificamos que no haya un nodo con ID superior en el anillo
        if (this.liderId == this.nodeId) {
            List<NodeInfo> ring = getActiveNodesInRing();
            boolean haySuperior = ring.stream().anyMatch(n -> n.getId() > this.nodeId);
            if (haySuperior) {
                log.warn("[MONITOR] Hay nodos con ID superior en el anillo. Re-iniciando elección para resolver liderazgo...");
                this.liderId = -1;
                iniciarEleccion();
            }
            return;
        }

        // Buscar al líder en Eureka
        List<NodeInfo> ring = getActiveNodesInRing();
        Optional<NodeInfo> leaderNode = ring.stream()
                .filter(n -> n.getId() == this.liderId)
                .findFirst();

        if (!leaderNode.isPresent()) {
            log.warn("[MONITOR] El líder {} no está registrado en Eureka. Iniciando elección...", liderId);
            iniciarEleccion();
            return;
        }

        // Si está en Eureka, hacerle ping de salud
        try {
            String pingUrl = leaderNode.get().getUri() + "/api/eleccion/ping";
            String response = directRestTemplate.getForObject(pingUrl, String.class);
            if (!"pong".equalsIgnoreCase(response)) {
                throw new RuntimeException("Respuesta de ping incorrecta");
            }
        } catch (Exception e) {
            log.warn("[MONITOR] Líder {} inalcanzable ({}). Iniciando elección desde {}...", liderId, e.getMessage(), nodeName);
            iniciarEleccion();
        }
    }

    // --- FUNCIÓN DE LIDERAZGO: Autorización de Préstamos Inter-Sede ---

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
            log.warn("[LIDERAZGO] Nodo {} NO es el líder. No puede autorizar préstamos.", nodeName);
            return false;
        }
        log.info("[LIDERAZGO] LÍDER {} AUTORIZA préstamo inter-sede del libro {} solicitado por {}",
                nodeName, libroId, sedeSolicitante);
        return true;
    }

    // Lado del solicitante: pide autorización al líder actual
    public boolean solicitarAutorizacionInterSede(UUID libroId, String sedeSolicitante) {
        Optional<NodeInfo> liderOpt = obtenerNodoLider();
        if (!liderOpt.isPresent()) {
            log.error("[LIDERAZGO] No se encontró al líder {} en el anillo.", liderId);
            return false;
        }

        NodeInfo lider = liderOpt.get();
        String url = lider.getUri()
                + "/api/eleccion/autorizar-prestamo-inter-sede"
                + "?libroId=" + libroId.toString()
                + "&sedeSolicitante=" + URLEncoder.encode(sedeSolicitante, StandardCharsets.UTF_8);

        try {
            log.info("[LIDERAZGO] Solicitando autorización al líder {} en {}", lider.getName(), url);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = directRestTemplate.getForObject(url, Map.class);
            boolean autorizado = response != null && Boolean.TRUE.equals(response.get("autorizado"));
            log.info("[LIDERAZGO] Respuesta del líder: autorizado={}", autorizado);
            return autorizado;
        } catch (Exception e) {
            log.error("[LIDERAZGO] Error al contactar al líder {}: {}", lider.getName(), e.getMessage());
            return false;
        }
    }
}
