# 📸 CAPTURAS VISUALES PARA SUSTENTACIÓN - LIBRONET

## Temas a Presentar
- ✅ **Tema 4**: Introducción a Tolerancia a Fallas
- ✅ **Tema 5**: Atenuación  
- ✅ **Tema 6**: Comunicación
- ✅ **Tema 7**: Recuperación de Fallas (RESYNC)

---

## 📌 TEMA 4: INTRODUCCIÓN A TOLERANCIA A FALLAS

### Concepto
La tolerancia a fallas es la capacidad del sistema de continuar operando cuando uno o más nodos fallan.

### Mecanismos Implementados
1. **Heartbeat periódico** (cada 5 segundos)
2. **Detección automática de falla de líder**
3. **Re-elección automática** sin intervención manual

### Endpoints para Demostración
```bash
GET /api/eleccion/estado      # Ver estado actual del nodo
GET /api/eleccion/eventos     # Ver historial de eventos
POST /api/eleccion/simular-caida?offline=true   # Simular caída
POST /api/eleccion/simular-caida?offline=false  # Restaurar
```

### Pantalla 1: Estado de Elección - Tolerancia a Fallas en Acción
**URL**: `http://localhost:8080/api/eleccion/estado`

```json
{
  "nodeName": "Sede Sur",
  "liderId": -1,                    // Sin líder actual
  "liderazgoEpoca": 2,              // Época de elección
  "estadoNode": "ELECTION",         // En proceso de elección
  "isOffline": false,               // Nodo activo
  "acceptingRequests": false,       // No acepta operaciones
  "nodeId": 2,
  "anillo": [
    {"name": "Sede Norte", "id": 1, "uri": "http://172.18.0.4:8081"},
    {"name": "Sede Sur", "id": 2, "uri": "http://172.18.0.5:8083"},
    {"name": "Sede Este", "id": 3, "uri": "http://172.18.0.6:8084"}
  ]
}
```

**Qué observar:**
- `liderId=-1` indica que no hay líder actual (falla detectada)
- `estadoNode="ELECTION"` muestra que el sistema está en proceso de re-elección
- `acceptingRequests=false` previene operaciones inconsistentes durante la transición
- El **anillo lógico** está completo: 3 nodos ordenados por ID

---

### Pantalla 2: Eventos de Tolerancia a Fallas
**URL**: `http://localhost:8080/api/eleccion/eventos`

**Eventos clave capturados:**
```
[EVENTO 1] Monitor detecta ausencia de lider. Iniciando eleccion.
           → DETECCIÓN: El heartbeat cada 5s detectó que el líder no responde

[EVENTO 2] Eleccion iniciada. term=1
           → TÉRMINO INCREMENTADO: Se dispara una nueva elección

[EVENTO 3] Anillo vacio durante eleccion; auto-liderazgo
           → AUTO-LIDERAZGO: Cuando el anillo está vacío, el nodo se auto-designa líder

[EVENTO 4] Nodo declarado lider. term=1
           → LÍDER ESTABLECIDO: Se establece un nuevo líder

[EVENTO 5] Nodo lider detecta IDs superiores activos. Re-eleccion preventiva.
           → RE-ELECCIÓN PREVENTIVA: Si hay nodos con ID superior, se cede el liderazgo

[EVENTO 6] Recibido ELECTION(2, term=2)
           → PROPAGACIÓN: Mensaje ELECTION viaja por el anillo
```

**Qué observar:**
- La **detección de falla es automática** (monitor cada 5s)
- La **re-elección ocurre sin intervención** del usuario
- Los **términos incrementan** para evitar split-brain
- Hay **redundancia**: Si hay un nodo superior, se reinicia la elección

---

## 📌 TEMA 5: ATENUACIÓN

### Concepto
Atenuación = técnicas para **reducir el impacto** de las fallas manteniendo operación degradada.

### Mecanismos Implementados
1. **Retry con backoff exponencial** (300ms → 2000ms)
2. **Rollback transaccional automático**
3. **Balanceo de carga** (round-robin)
4. **Degradación selectiva** (operaciones locales sin líder)

---

### Pantalla 3: Balanceo de Carga (Round-Robin)
**URL**: `http://localhost:8080/api/catalogo/instancia`

```json
{
  "instance": "91a3e12bd99c:8082",
  "service": "libronet-catalogo"
}
```

**Qué observar:**
- El **identificador de instancia** cambia con cada petición
- Esto demuestra que el gateway **distribuye** peticiones entre réplicas
- **Atenuación de sobrecarga**: Si una instancia está saturada, la otra toma la carga

---

### Pantalla 4: Retry con Backoff Exponencial (en eventos)
**Eventos que demuestran atenuación:**

```
[INTENTO 1] Solicitar autorizacion inter-sede
            → Sin líder disponible, disparar elección
            → Espera 300ms (backoff inicial)

[INTENTO 2] Reintento después de 300ms
            → Aún sin líder
            → Espera 600ms (backoff * 2)

[INTENTO 3] Reintento después de 600ms
            → Aún sin líder
            → Espera 1200ms (backoff * 2)

[INTENTO 4-6] Reintentos con 2000ms (tope máximo)
             → Si se alcanza 6 intentos sin éxito → ROLLBACK

[CONVERGENCIA] Nuevo líder elegido
               → Autorización concedida
               → Operación se completa
```

**Qué observar:**
- **Backoff exponencial**: 300 → 600 → 1200 → 2000 ms
- **Máximo 6 reintentos**: No espera indefinidamente
- **Rollback automático**: Si falla, toda la transacción se revierte
- **Convergencia**: Eventualmente se elige un nuevo líder

---

## 📌 TEMA 6: COMUNICACIÓN

### Concepto
Comunicación distribuida mediante HTTP/REST + descubrimiento dinámico + P2P directo.

### Capas de Comunicación

#### **Capa 1: Externa (Frontend → Gateway → Servicios)**
```
Frontend (5173)
    ↓
API Gateway (8080)  [Punto único de entrada]
    ↓
Servicios (round-robin)
```

#### **Capa 2: Interna (Servicio → Servicio vía Eureka)**
```
Servicio A → Discovery (Eureka)
             ↓ (obtiene dirección)
             Servicio B
```

#### **Capa 3: P2P para Elección (Directo entre nodos)**
```
Nodo 1 → Nodo 2 → Nodo 3 → Nodo 1 (anillo lógico)
(sin pasar por Gateway)
```

---

### Pantalla 5: Eureka Service Registry
**URL**: `http://localhost:8761/`

**Servicios registrados:**
```
LIBRONET-API-GATEWAY (1 instancia)
  └─ 976a42cf2e8a:libronet-api-gateway:8080

LIBRONET-CATALOGO (2 instancias)
  ├─ 91a3e12bd99c:libronet-catalogo:8082
  └─ f7ed6fa6fe53:libronet-catalogo:8082

LIBRONET-PRESTAMOS (3 instancias)
  ├─ libronet-prestamos:8081 (Norte, nodeId=1)
  ├─ libronet-prestamos:8083 (Sur, nodeId=2)
  └─ libronet-prestamos:8084 (Este, nodeId=3)
```

**Qué observar:**
- **Descubrimiento dinámico**: Todos los servicios se registran automáticamente
- **Metadata**: Cada nodo tiene nodeId y sede asignados
- **Redundancia**: Catálogo tiene 2 réplicas para balanceo
- **Sin configuración estática**: Los servicios descubren otros por nombre lógico

---

### Pantalla 6: Frontend - Interfaz de Comunicación
**URL**: `http://localhost:5173/`

**Componentes visuales:**
```
┌─────────────────────────────────┐
│     LIBRONET                    │
│ Panel de Control de Sedes      │
│                                │
│ [Identificador del Bibliotecario]
│ [Contraseña de Red]             │
│ [Conectarse a Sede ▼]          │
│                                │
│ [🔐 Iniciar Conexión de Sede]  │
└─────────────────────────────────┘
```

**Qué observar:**
- La **interfaz se comunica con el Gateway**
- Múltiples **sedes disponibles** (Norte, Sur, Este)
- **Autenticación distribuida**: Validada en nodos
- **Headers de trazabilidad**: Rastrean peticiones entre servicios

---

### Pantalla 7: Headers de Trazabilidad
**Cuando se realiza una petición HTTP:**

```
REQUEST:
  GET /api/prestamos/{id}
  Authorization: Bearer ...

RESPONSE HEADERS:
  X-Gateway-Routed-Host: 172.18.0.5:8083
  X-LibroNet-Instance: Sede-Sur-2
  
LOGS:
  [ROUTING] method=GET path=/api/prestamos/... 
            target=172.18.0.5:8083
```

**Qué observar:**
- **`X-Gateway-Routed-Host`**: Muestra cuál nodo procesó la petición
- **`X-LibroNet-Instance`**: Identificador de instancia
- **Auditoría completa**: Todas las peticiones quedan registradas
- **Trazabilidad de comunicación**: Pueden seguirse todas las interacciones

---

## 📌 TEMA 7: RECUPERACIÓN DE FALLAS (RESYNC)

### Concepto
**Recuperación** = Protocolo para que un nodo caído se reincorpore consistentemente.

### Proceso de RESYNC (3 Fases)

```
FASE 1: Sincronización de Reloj (Cristian)
├─ Contacta al servidor de tiempo (API Gateway)
├─ Recalcula su reloj con RTT/2
└─ Obtiene marca temporal sincronizada

FASE 2: Consulta del Anillo Activo
├─ Consulta Eureka para encontrar nodos activos
├─ Obtiene estado de TODOS los nodos
├─ Verifica si hay líder vigente
└─ Adopta o inicia nueva elección

FASE 3: Habilitación de Negocio
├─ Si encuentra líder vigente → adopta
├─ Si converge en nueva elección → espera resultado
├─ Establece acceptingRequests = true
└─ Cambia estado: RESYNC → NORMAL
```

---

### Pantalla 8: Nodo Offline - Estado de Caída
**Comando:**
```bash
POST /api/eleccion/simular-caida?offline=true
```

**Estado resultante:**
```json
{
  "nodeName": "Sede Este",
  "isOffline": true,           // ← Nodo caído
  "estadoNode": "OFFLINE",     // ← Estado: OFFLINE
  "acceptingRequests": false,  // ← NO acepta operaciones
  "liderId": -1,
  "liderazgoEpoca": 2
}
```

**Qué observar:**
- `isOffline=true`: Nodo marcado como caído
- `estadoNode="OFFLINE"`: Máquina de estados en OFFLINE
- `acceptingRequests=false`: Rechaza todas las operaciones (HTTP 503)
- **Impacto**: El anillo detecta su ausencia en el siguiente heartbeat

---

### Pantalla 9: Nodo en RESYNC - Recuperación en Progreso
**Comando:**
```bash
POST /api/eleccion/simular-caida?offline=false
```

**Estado durante RESYNC:**
```json
{
  "nodeName": "Sede Este",
  "isOffline": false,          // ← Nodo restaurado
  "estadoNode": "RESYNC",      // ← En proceso de recuperación
  "acceptingRequests": false,  // ← Aún no acepta operaciones
  "lastClockSyncMs": 1689255323456,  // ← Reloj sincronizado
  "liderazgoEpoca": 4          // ← Actualizado con el anillo
}
```

**Qué observar:**
- `estadoNode="RESYNC"`: Máquina en fase de recuperación
- `lastClockSyncMs`: Se actualizó (sincronización de reloj completada)
- `liderazgoEpoca=4`: Se sincronizó con elecciones que ocurrieron en su ausencia
- `acceptingRequests=false`: **Protección crítica**: No acepta operaciones hasta NORMAL
- **Duración típica**: 2-8 segundos

---

### Pantalla 10: Nodo Recuperado - Estado NORMAL
**Después de RESYNC exitoso:**

```json
{
  "nodeName": "Sede Este",
  "isOffline": false,
  "estadoNode": "NORMAL",      // ← Estado: NORMAL
  "acceptingRequests": true,   // ← ✅ Acepta operaciones
  "liderId": 3,                // ← Líder conocido
  "liderazgoEpoca": 4,         // ← Sincronizado
  "lastClockSyncMs": 1689255323456
}
```

**Qué observar:**
- `estadoNode="NORMAL"`: Transición exitosa de RESYNC → NORMAL
- `acceptingRequests=true`: **Habilitación**: Ahora acepta operaciones
- `liderId=3`: Conoce quién es el líder actual
- **Convergencia**: Todo el anillo está en acuerdo
- **Consistencia**: No hay split-brain

---

### Máquina de Estados Completa

```
                    ┌─────────┐
                    │ NORMAL  │ ← Estado operacional
                    └────┬────┘
                         │ (Detecta falta de líder)
                         ▼
                    ┌──────────┐
                    │ ELECTION │ ← Re-elección
                    └────┬─────┘
                         │ (Se elige nuevo líder)
                         ▼
                    ┌─────────┐
                    │ NORMAL  │ ← Vuelve a operación
                    └────▲────┘
                         │
        (Usuario simula  │ (Usuario restaura nodo)
         caída)          │
                    ┌────┴────┐
                    │ OFFLINE  │
                    └────┬─────┘
                         │
                    ┌────▼─────┐
                    │  RESYNC   │ ← Recuperación
                    │ (3 fases) │
                    └────┬─────┘
                         │
                    ┌────▼─────┐
                    │ NORMAL    │ ← Operacional nuevamente
                    └───────────┘
```

---

## 🎯 RESUMEN VISUAL POR TEMA

| Tema | Evidencia Visual | Endpoint/URL | Qué Demostrar |
|------|---|---|---|
| **Tolerancia a Fallas** | Estado de elección | `/api/eleccion/estado` | `liderId=-1`, `estadoNode="ELECTION"` |
| **Tolerancia a Fallas** | Eventos históricos | `/api/eleccion/eventos` | Detección automática, re-elección |
| **Atenuación** | Balanceo round-robin | `/api/catalogo/instancia` | Distribución de carga |
| **Atenuación** | Retry con backoff | `/api/eleccion/eventos` | Reintentos exponenciales (300→2000ms) |
| **Comunicación** | Eureka registry | `http://localhost:8761/` | Descubrimiento dinámico |
| **Comunicación** | Frontend | `http://localhost:5173/` | Interfaz HTTP/REST |
| **Comunicación** | Headers de trazabilidad | Response headers | `X-Gateway-Routed-Host`, `X-LibroNet-Instance` |
| **Recuperación** | Estado OFFLINE | POST `/simular-caida?offline=true` | Nodo caído, no acepta ops |
| **Recuperación** | Estado RESYNC | POST `/simular-caida?offline=false` | Recuperación en progreso |
| **Recuperación** | Estado NORMAL | GET `/api/eleccion/estado` | Operacional post-recuperación |

---

## 🚀 FLUJO DE DEMOSTRACIÓN RECOMENDADO

### **Demo 1: Tolerancia a Fallas (5 minutos)**
1. Abrir `http://localhost:8080/api/eleccion/estado`
   - Mostrar que hay un líder actual (ej: liderId=3)
   - Mostrar anillo lógico completo (3 nodos)

2. Abrir `http://localhost:8080/api/eleccion/eventos`
   - Mostrar eventos de elección pasados
   - Explicar detección de falla (heartbeat cada 5s)

3. Simular caída:
   ```bash
   curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true"
   ```

4. Volver a `http://localhost:8080/api/eleccion/estado`
   - Mostrar `liderId=-1` (sin líder)
   - Mostrar `estadoNode="ELECTION"` (re-elección en progreso)
   - **Explicar**: El monitor detectó la falla y disparó nueva elección

5. Esperar 10-15 segundos
   - Volver a `/api/eleccion/estado`
   - Mostrar nuevo `liderId` elegido (debe ser otro nodo)
   - **Explicar**: Re-elección automática completada sin intervención

---

### **Demo 2: Atenuación (3 minutos)**
1. Abrir `http://localhost:8080/api/catalogo/instancia` 5 veces
   - Mostrar que el `instance` cambia
   - **Explicar**: Balanceo round-robin en acción

2. Abrir `http://localhost:8080/api/eleccion/eventos`
   - Mostrar eventos de reintentos
   - **Explicar**: Backoff exponencial (300 → 600 → 1200 → 2000ms)

---

### **Demo 3: Comunicación (3 minutos)**
1. Abrir `http://localhost:8761/`
   - Mostrar tabla de servicios registrados
   - Explicar que se registran automáticamente sin config estática
   - Señalar 2 réplicas de catálogo, 3 nodos de préstamos

2. Abrir `http://localhost:5173/`
   - Mostrar interfaz del usuario
   - Explicar conexión HTTP/REST con el gateway

3. Abrir DevTools (F12) en el frontend
   - Mostrar headers de respuesta: `X-Gateway-Routed-Host`, `X-LibroNet-Instance`
   - Explicar trazabilidad

---

### **Demo 4: Recuperación de Fallas (5 minutos)**
1. Nodo actualmente NORMAL
   ```bash
   curl -s http://localhost:8080/api/eleccion/estado | grep -o '"estadoNode":"[^"]*"'
   # Output: "estadoNode":"NORMAL"
   ```

2. Simular caída:
   ```bash
   curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true"
   ```

3. Ver estado OFFLINE:
   ```bash
   curl -s http://localhost:8080/api/eleccion/estado | grep -E '"estadoNode"|"isOffline"|"acceptingRequests"'
   # Output: "estadoNode":"OFFLINE", "isOffline":true, "acceptingRequests":false
   ```
   - **Explicar**: Nodo no acepta operaciones (HTTP 503)

4. Restaurar nodo:
   ```bash
   curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false"
   ```

5. Ver estado RESYNC (2-8 segundos):
   ```bash
   curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'
   # Output: "estadoNode":"RESYNC"
   ```
   - **Explicar**: Fase 1 (sincronización de reloj Cristian)
   - **Explicar**: Fase 2 (consulta de anillo activo)
   - **Explicar**: Fase 3 (habilitación gradual)

6. Ver estado NORMAL (post-RESYNC):
   ```bash
   curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'
   # Output: "estadoNode":"NORMAL"
   ```
   - **Explicar**: Transición exitosa
   - **Explicar**: acceptingRequests=true (acepta operaciones nuevamente)

---

## 📊 RESUMEN EJECUTIVO

| Aspecto | Evidencia | Impacto |
|---------|-----------|--------|
| **Tolerancia a Fallas** | Detección automática + re-elección sin intervención | Sistema sigue operando sin operador |
| **Atenuación** | Retry con backoff + rollback automático | Minimiza impacto de fallas transitorias |
| **Comunicación** | Descubrimiento dinámico + trazabilidad | Arquitectura flexible y auditable |
| **Recuperación** | RESYNC en 3 fases + protección against split-brain | Nodos recuperados consistentemente |

---

**Última actualización**: 13/07/2026  
**Estado**: ✅ Listo para sustentación visual
