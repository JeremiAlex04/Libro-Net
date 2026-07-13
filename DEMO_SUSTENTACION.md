# 🎬 GUÍA DE DEMOSTRACIÓN EN VIVO - LIBRONET

## Requisitos Previos

1. **Docker debe estar corriendo**:
```bash
cd d:/Proyectos/bibliotecaDistribuido
docker-compose up -d
```

2. **Esperar 20-30 segundos** para que todos los servicios estén listos

3. **Verificar servicios**:
```bash
docker-compose ps
```

---

## URLs DE DEMOSTRACIÓN

### 🌐 Interfaces Web

| Interfaz | URL | Puerto | Función |
|----------|-----|--------|---------|
| **Frontend** | http://localhost:5173 | 5173 | Panel de control de sedes |
| **Eureka** | http://localhost:8761 | 8761 | Descubrimiento dinámico |
| **API Gateway** | http://localhost:8080 | 8080 | Punto de entrada único |

### 📡 Endpoints para Demostración

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/api/eleccion/estado` | GET | Estado actual del nodo |
| `/api/eleccion/eventos` | GET | Historial de eventos |
| `/api/eleccion/simular-caida?offline=true` | POST | Simular caída |
| `/api/eleccion/simular-caida?offline=false` | POST | Restaurar nodo |
| `/api/catalogo/instancia` | GET | Balanceo round-robin |
| `/api/time` | GET | Servidor de tiempo (Cristian) |

---

## 🎯 DEMO 1: TOLERANCIA A FALLAS (5-7 minutos)

### Paso 1: Ver estado actual
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool
```

**Buscar**:
- ✅ `"liderId"`: Debe tener un número (1, 2 o 3)
- ✅ `"estadoNode": "NORMAL"`
- ✅ `"acceptingRequests": true`

### Paso 2: Ver historial de eventos
```bash
curl -s http://localhost:8080/api/eleccion/eventos | python -m json.tool | head -30
```

**Buscar eventos**:
- "Monitor detecta ausencia de lider"
- "Eleccion iniciada"
- "Nodo declarado lider"

### Paso 3: Simular caída del líder actual
```bash
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true" -s
```

**Resultado esperado**:
```
Estado de caída cambiado a: true
```

### Paso 4: Verificar impacto inmediato
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool
```

**Cambios esperados** (al verificar de inmediato):
- ⚠️ `"estadoNode": "OFFLINE"`
- ⚠️ `"acceptingRequests": false`
- ⚠️ `"isOffline": true`

**Explicar**:
> "El nodo está caído y no acepta operaciones. El sistema detectará esto con el heartbeat cada 5 segundos"

### Paso 5: Esperar 10-15 segundos y verificar re-elección

```bash
# Esperar un poco
# Luego consultar eventos nuevos
curl -s http://localhost:8080/api/eleccion/eventos | python -m json.tool | tail -20
```

**Buscar nuevos eventos**:
- "Enviando ELECTION(..."
- "Recibido ELECTION(..."
- "Nodo declarado lider" (con nueva época)

### Paso 6: Verificar nuevo líder elegido
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool
```

**Cambios esperados**:
- ✅ `"liderId": 2 o 3` (distinto del anterior)
- ✅ `"liderazgoEpoca": 3 o 4` (incrementado)
- ✅ `"estadoNode": "NORMAL"`
- ✅ `"acceptingRequests": true`

**Conclusión**:
> "El sistema automáticamente eligió un nuevo líder sin intervención manual. Esta es la tolerancia a fallas en acción."

---

## 🎯 DEMO 2: ATENUACIÓN (3-5 minutos)

### Paso 1: Demostrar balanceo round-robin

Ejecutar múltiples veces y observar cambio de instancia:

```bash
# Ejecutar 5 veces seguidas
for i in {1..5}; do
  echo "=== Petición $i ==="
  curl -s http://localhost:8080/api/catalogo/instancia | python -m json.tool
done
```

**Observar**:
- El campo `"instance"` cambia en cada petición
- Los valores alternados: `91a3e12bd99c:8082` ↔ `f7ed6fa6fe53:8082`
- Esto demuestra **distribución de carga**

**Explicar**:
> "El gateway distribuye las peticiones con round-robin. Si una instancia está saturada, la otra toma la carga. Esto es atenuación de sobrecarga."

### Paso 2: Demostrar retry con backoff exponencial

Restaurar el nodo caído pero mantener un líder "lento":

```bash
# Primero restaurar
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false" -s

# Ver eventos
curl -s http://localhost:8080/api/eleccion/eventos | python -m json.tool
```

**Buscar eventos de reintentos**:
- Timestamp differences entre ELECTION messages
- Incremento exponencial de tiempos

**Explicar backoff**:
```
Intento 1: Espera 300ms
Intento 2: Espera 600ms
Intento 3: Espera 1200ms
Intento 4-6: Espera 2000ms (tope máximo)
```

> "Si el líder falla durante una autorización inter-sede, el sistema reinenta con backoff exponencial. Esto evita saturar la red con reintentos inmediatos."

### Paso 3: Demonstrar protección de transacciones

**Explicar (no requiere ejecución)**:
> "Si los 6 reintentos se agotan sin lograr autorización del líder, la transacción hace ROLLBACK automático. Esto previene estados inconsistentes en la base de datos."

Mostrar en el código:
```java
@Transactional
public String procesarPrestamo(...) {
    // Si no hay autorización → RuntimeException
    if (!autorizado) {
        throw new RuntimeException(...);
        // La excepción causa ROLLBACK automático
    }
}
```

---

## 🎯 DEMO 3: COMUNICACIÓN (4-6 minutos)

### Paso 1: Abrir Eureka para ver descubrimiento dinámico

**URL**: http://localhost:8761/

**Explicar qué ves**:
```
Servicios registrados:
├─ LIBRONET-API-GATEWAY (1 instancia)
├─ LIBRONET-CATALOGO (2 instancias)  ← Réplicas para balanceo
└─ LIBRONET-PRESTAMOS (3 instancias)  ← Una por sede
```

**Señalar**:
> "Todos los servicios se auto-registran dinámicamente. No hay configuración estática de direcciones IP. Si un servicio se cae y vuelve a levantarse, Eureka automáticamente lo detecta."

### Paso 2: Mostrar Frontend

**URL**: http://localhost:5173/

**Explicar flujo de comunicación**:
```
Frontend (React)
    ↓ (HTTP/REST)
API Gateway (puerto 8080)
    ↓ (Découvery vía Eureka + Load Balancing)
Microservicios
    ↓
PostgreSQL (BD centralizada)
```

### Paso 3: Ver headers de trazabilidad

Abrir DevTools (F12) en el navegador y hacer una petición:

```bash
# En la terminal
curl -i http://localhost:8080/api/catalogo/buscar?query=libro
```

**Buscar en headers de respuesta**:
```
X-Gateway-Routed-Host: 172.18.0.8:8082
X-LibroNet-Instance: libronet-catalogo:91a3e12bd99c
```

**Explicar**:
> "Cada petición queda traceable. Podemos ver exactamente qué nodo procesó la petición y cómo llegó allí. Esto es auditoría distribuida."

### Paso 4: Ver anillo lógico de elección

```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -A 10 "anillo"
```

**Resultado esperado**:
```json
"anillo": [
  {"name": "Sede Norte", "id": 1, "uri": "http://172.18.0.4:8081"},
  {"name": "Sede Sur", "id": 2, "uri": "http://172.18.0.5:8083"},
  {"name": "Sede Este", "id": 3, "uri": "http://172.18.0.6:8084"}
]
```

**Explicar**:
> "El anillo se construye dinámicamente consultando Eureka. Los nodos se comunican P2P (sin pasar por Gateway) para mensajes de elección. Esto reduce latencia de coordinación."

---

## 🎯 DEMO 4: RECUPERACIÓN DE FALLAS (6-8 minutos)

### Paso 1: Ver estado NORMAL inicial

```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | \
  grep -E '"estadoNode"|"acceptingRequests"|"isOffline"'
```

**Resultado esperado**:
```
"estadoNode": "NORMAL"
"acceptingRequests": true
"isOffline": false
```

**Explicar**:
> "El nodo está operacional: acepta peticiones y está online."

### Paso 2: Simular caída
```bash
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true" -s
```

### Paso 3: Ver estado OFFLINE

Inmediatamente después:
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | \
  grep -E '"estadoNode"|"acceptingRequests"|"isOffline"'
```

**Resultado esperado**:
```
"estadoNode": "OFFLINE"
"acceptingRequests": false
"isOffline": true
```

**Explicar**:
> "El nodo está caído:
> - No acepta peticiones (HTTP 503)
> - Está marcado como offline
> - No participa en elecciones"

### Paso 4: Intentar operación → Falla esperada

```bash
curl -s http://localhost:8080/api/prestamos/1 -H "Content-Type: application/json" | head -20
```

**Resultado esperado** (HTTP 503):
```
Nodo en resincronizacion. Reintente en unos segundos.
```

**Explicar**:
> "El sistema rechaza las peticiones de negocio mientras recupera. Esto previene inconsistencias."

### Paso 5: Restaurar nodo
```bash
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false" -s
```

### Paso 6: Observar estado RESYNC (2-8 segundos después)

```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | \
  grep -E '"estadoNode"|"acceptingRequests"|"isOffline"|"lastClockSyncMs"|"liderazgoEpoca"'
```

**Resultado esperado durante RESYNC**:
```
"estadoNode": "RESYNC"
"acceptingRequests": false
"isOffline": false
"lastClockSyncMs": 1689255323456  ← Se actualizó
"liderazgoEpoca": 4  ← Se sincronizó con elecciones pasadas
```

**Explicar las 3 fases de RESYNC**:

**Fase 1: Sincronización de Reloj**
> "El nodo recuperado consulta el servidor de tiempo (API Gateway) para sincronizar su reloj usando el Algoritmo de Cristian. Recalcula: T_corregido = T_server + RTT/2"

**Fase 2: Consulta del Anillo Activo**
> "Consulta todos los nodos activos para encontrar el líder actual y sincronizar la época de elecciones. Si no encuentra líder vigente, inicia nueva elección."

**Fase 3: Protección contra split-brain**
> "No acepta operaciones de negocio hasta convergencia completa. Esto previene que el nodo recuperado cree inconsistencias."

### Paso 7: Observar transición a NORMAL (8-15 segundos)

```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | \
  grep -E '"estadoNode"|"acceptingRequests"|"isOffline"'
```

**Resultado esperado post-RESYNC**:
```
"estadoNode": "NORMAL"
"acceptingRequests": true
"isOffline": false
```

**Explicar**:
> "Transición exitosa:
> - El reloj fue sincronizado ✅
> - Se convergió a un líder único ✅
> - El estado es consistente ✅
> - Acepta operaciones nuevamente ✅
> 
> La recuperación fue completamente automática sin intervención manual."

### Paso 8: Verificar consistencia de datos

```bash
# Verificar que todos los nodos conocen el mismo líder
curl -s http://localhost:8080/api/eleccion/estado | grep '"liderId"'

# Verificar que todos están en NORMAL
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'
```

---

## 📋 SCRIPT DE DEMOSTRACIÓN COMPLETA (Opcional)

Crear archivo `demo.sh`:

```bash
#!/bin/bash

echo "========================================="
echo "DEMO LIBRONET - Sistemas Distribuidos"
echo "========================================="

# DEMO 1: Tolerancia a Fallas
echo ""
echo "🔴 DEMO 1: TOLERANCIA A FALLAS"
echo "Estado actual:"
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"liderId"|"estadoNode"'

echo ""
echo "Simulando caída del nodo..."
curl -s -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true" > /dev/null

echo "Estado post-caída (inmediato):"
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"liderId"|"estadoNode"'

echo ""
echo "Esperando 15 segundos para que se complete la re-elección..."
sleep 15

echo "Estado post-re-elección:"
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"liderId"|"estadoNode"|"liderazgoEpoca"'

# DEMO 2: Atenuación
echo ""
echo "🟡 DEMO 2: ATENUACIÓN"
echo "Demostrando balanceo round-robin (5 peticiones):"
for i in {1..5}; do
  curl -s http://localhost:8080/api/catalogo/instancia | python -m json.tool | grep '"instance"'
done

# DEMO 3: Comunicación
echo ""
echo "🟢 DEMO 3: COMUNICACIÓN"
echo "Servicios en Eureka:"
curl -s http://localhost:8761/eureka/apps | python -m json.tool 2>/dev/null | grep -o '"name":"[^"]*"' | head -5

# DEMO 4: Recuperación de Fallas
echo ""
echo "🔵 DEMO 4: RECUPERACIÓN DE FALLAS (RESYNC)"

echo "Restaurando nodo..."
curl -s -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false" > /dev/null

echo "Estado en RESYNC (2-8 segundos):"
sleep 3
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"estadoNode"|"acceptingRequests"'

echo ""
echo "Esperando convergencia..."
sleep 5

echo "Estado NORMAL post-RESYNC:"
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"estadoNode"|"acceptingRequests"'

echo ""
echo "========================================="
echo "✅ DEMO COMPLETADA"
echo "========================================="
```

Ejecutar:
```bash
bash demo.sh
```

---

## 🎓 NOTAS PARA EL EXPOSITOR

### Puntos clave a enfatizar en cada tema:

**Tema 4: Tolerancia a Fallas**
- ✅ Heartbeat automático cada 5 segundos
- ✅ Detección de falla es automática
- ✅ Re-elección sin intervención manual
- ✅ Sistema sigue operando con nuevo líder

**Tema 5: Atenuación**
- ✅ Balanceo distribuye carga
- ✅ Retry con backoff exponencial
- ✅ Rollback automático protege consistencia
- ✅ Degradación selectiva: ops locales sin líder

**Tema 6: Comunicación**
- ✅ Descubrimiento dinámico (Eureka)
- ✅ Sin configuración estática
- ✅ Trazabilidad mediante headers
- ✅ P2P directo para elección, HTTP/REST para negocio

**Tema 7: Recuperación de Fallas**
- ✅ 3 fases: Reloj → Anillo → Habilitación
- ✅ Protección contra split-brain
- ✅ Estados claros: OFFLINE → RESYNC → NORMAL
- ✅ No acepta negocio hasta NORMAL

---

**Versión**: 1.0  
**Fecha**: 13/07/2026  
**Estado**: ✅ Listo para demostración en vivo
