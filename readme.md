# 📚 LibroNet — Sistema Distribuido de Gestión Bibliotecaria

<div align="center">

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![React](https://img.shields.io/badge/React_18-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Eureka](https://img.shields.io/badge/Netflix_Eureka-E50914?style=for-the-badge&logo=netflix&logoColor=white)

**Sistema distribuido de préstamo y logística inter-bibliotecaria con exclusión mutua,  
sincronización de relojes y balanceo de carga.**

</div>

---

## 📋 Tabla de Contenidos

1. [Descripción del Sistema](#-descripción-del-sistema)
2. [Escenario de Funcionamiento](#-escenario-de-funcionamiento)
3. [Arquitectura General](#-arquitectura-general)
4. [Componentes del Sistema](#-componentes-del-sistema)
5. [Semana 13 — Exclusión Mutua: Algoritmo de Dekker](#-semana-13--exclusión-mutua-algoritmo-de-dekker)
6. [Flujo Completo de un Préstamo](#-flujo-completo-de-un-préstamo)
7. [Sincronización de Relojes — Algoritmo de Cristian](#-sincronización-de-relojes--algoritmo-de-cristian)
8. [Logística Inter-Sedes](#-logística-inter-sedes)
9. [Levantamiento del Sistema](#-levantamiento-del-sistema)
10. [Endpoints de la API](#-endpoints-de-la-api)

---

## 📖 Descripción del Sistema

**LibroNet** es un sistema distribuido de gestión bibliotecaria diseñado para coordinar el préstamo de libros entre dos sedes geográficamente separadas (**Sede Norte** y **Sede Sur**) sin un controlador central que genere un punto único de falla.

El sistema implementa conceptos fundamentales de **sistemas distribuidos**:

| Concepto | Implementación |
|----------|---------------|
| 🔒 Exclusión Mutua | Algoritmo de Dekker (Versión 5) con variables `volatile` compartidas |
| 🕐 Sincronización de Relojes | Algoritmo de Cristian vía `GET /api/time` en el API Gateway |
| ⚖️ Balanceo de Carga | Netflix Eureka + Spring Cloud Gateway (round-robin entre nodos) |
| 🗄️ Consistencia de Datos | Transacciones ACID con bloqueo pesimista (`SELECT FOR UPDATE`) |
| 🚚 Logística Distribuida | Estados de préstamo: `PENDIENTE_DE_ENVIO → EN_TRANSITO → ENTREGADO` |

---

## 🏛️ Escenario de Funcionamiento

La biblioteca opera bajo el siguiente escenario real:

> **Una red universitaria con dos sedes físicas** — Sede Norte y Sede Sur — que comparten un catálogo de libros unificado. Cada sede tiene su propio inventario de ejemplares físicos, pero ambas pueden realizar préstamos sobre el stock de la otra sede cuando el suyo está agotado.

### Condiciones del escenario

```
┌─────────────────────────────────────────────────────────────────┐
│  RESTRICCIÓN PRINCIPAL: Solo UN préstamo puede procesarse       │
│  a la vez sobre un mismo libro (recurso compartido crítico).    │
│  Si ambas sedes solicitan el último ejemplar simultáneamente,   │
│  el sistema DEBE garantizar que solo una lo obtenga.            │
└─────────────────────────────────────────────────────────────────┘
```

**Actores del sistema:**
- 👤 **Bibliotecario** — Empleado autenticado por sede y rol
- 📗 **Libro** — Recurso con stock separado por sede (`copiasNorte`, `copiasSur`) y enlace digital
- 🏢 **Sede** — Nodo distribuido con su propia instancia del servicio de préstamos
- 🌐 **Gateway** — Único punto de entrada externo; actúa también como servidor de tiempo de referencia

---

## 🏗️ Arquitectura General

```
                         ┌─────────────────────────────────┐
                         │         INTERNET / CLIENTE       │
                         │    Frontend React (puerto 5173)  │
                         └────────────────┬────────────────┘
                                          │ HTTP
                         ┌────────────────▼────────────────┐
                         │         API GATEWAY              │
                         │   Spring Cloud Gateway :8080     │
                         │   ┌──────────────────────────┐  │
                         │   │  /api/time  (Ref. Clock) │  │
                         │   └──────────────────────────┘  │
                         └──────┬────────────┬─────────────┘
                                │            │
              ┌─────────────────▼──┐   ┌─────▼──────────────┐
              │  CATÁLOGO SERVICE  │   │  PRÉSTAMOS SERVICE  │
              │  Spring Boot :808x │   │  (Load Balanced)    │
              │  ┌──────────────┐  │   │  ┌───────────────┐  │
              │  │ /api/catalogo│  │   │  │ Nodo NORTE    │  │
              │  └──────────────┘  │   │  │ (puerto rand.)│  │
              └─────────┬──────────┘   │  ├───────────────┤  │
                        │              │  │ Nodo SUR      │  │
              ┌─────────▼──────────┐   │  │ (puerto rand.)│  │
              │  Netflix Eureka    │   │  └───────────────┘  │
              │  Service Registry  │   └─────────┬───────────┘
              │     :8761          │             │
              └────────────────────┘   ┌─────────▼───────────┐
                                       │    PostgreSQL :5435   │
                                       │    biblioteca_db      │
                                       │  ┌─────┐  ┌───────┐  │
                                       │  │libro│  │presta-│  │
                                       │  │     │  │  mo   │  │
                                       │  └─────┘  └───────┘  │
                                       └──────────────────────┘
```

---

## 🧩 Componentes del Sistema

### 1. `_frontend-libronet` — Interfaz de Usuario (React + Vite)

Aplicación SPA construida con React 18. Implementa:
- **Autenticación por sede**: Login de bibliotecario validado contra la base de datos; el acceso es restringido según sede y rol.
- **Catálogo en tiempo real**: Búsqueda de libros con auto-polling cada 5 segundos para reflejar cambios concurrentes.
- **Actualización optimista**: La UI descuenta el inventario visualmente de forma inmediata, con rollback si el servidor rechaza la operación.
- **Panel de Logística**: Vista bifurcada (logística activa / historial) con gestión de estados inter-sedes.
- **Modo Auditoría**: Revela los datos de sincronización de Cristian (Drift, RTT, hora corregida) por préstamo.

### 2. `api-gateway` — Spring Cloud Gateway

Punto de entrada único para todas las peticiones HTTP externas. Responsabilidades:
- **Enrutamiento dinámico** hacia `catalogo-service` y `prestamos-service` vía Eureka.
- **Balanceo de carga** automático entre los nodos `prestamos-norte` y `prestamos-sur` (round-robin).
- **Servidor de Tiempo de Referencia** — Expone `GET /api/time` que retorna `serverTimeMs`, utilizado por cada nodo para ejecutar el Algoritmo de Cristian.

### 3. `catalogo-service` — Catálogo de Libros

Microservicio de solo lectura que expone el inventario centralizado de libros con búsqueda por título/autor.

### 4. `prestamos-service` — Motor de Préstamos (×2 instancias)

El servicio más crítico del sistema. Se despliega en **dos instancias simultáneas** (`prestamos-norte` y `prestamos-sur`), ambas registradas en Eureka. Responsabilidades:
- Procesar solicitudes de préstamo con bloqueo transaccional pesimista.
- Aplicar el **Algoritmo de Cristian** para corregir el timestamp de cada operación.
- Proveer el endpoint de simulación del **Algoritmo de Dekker** para demostración de exclusión mutua.
- Gestionar el ciclo de vida logístico de los préstamos inter-sedes.

### 5. `red` — Netflix Eureka Server

Registro de servicios (Service Registry). Todos los microservicios se registran aquí al arrancar; el Gateway consulta este registro para enrutar dinámicamente sin IPs hardcodeadas.

### 6. PostgreSQL — Base de Datos Compartida

Base de datos relacional única compartida por todas las instancias. Las tablas principales son:

| Tabla | Descripción |
|-------|-------------|
| `libro` | Inventario con `copias_norte` y `copias_sur` separadas por sede |
| `prestamo` | Registro auditable de cada transacción con timestamps de Cristian |
| `bibliotecario` | Usuarios del sistema con sede y rol asociados |

---

## 🔒 Semana 13 — Exclusión Mutua: Algoritmo de Dekker

### Contexto del Problema

Cuando **dos sedes solicitan simultáneamente el último ejemplar físico** de un libro, se produce una condición de carrera (*race condition*) sobre el inventario. Sin un mecanismo de exclusión mutua, ambas podrían leer `stock = 1`, ambas decrementarlo, y terminar con `stock = -1` — una inconsistencia crítica.

### Solución Implementada en el Sistema Real

El sistema utiliza **bloqueo pesimista a nivel de base de datos** (`SELECT FOR UPDATE` vía `@Lock(PESSIMISTIC_WRITE)` en el repositorio JPA). Esto garantiza que solo una transacción pueda leer y modificar el inventario de un libro a la vez a nivel de producción.

```java
// LibroRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT l FROM Libro l WHERE l.id = :id")
Optional<Libro> findByIdForUpdate(@Param("id") UUID id);
```

### Simulación Didáctica — Algoritmo de Dekker (Versión 5)

Se implementó adicionalmente un endpoint de simulación que demuestra el **Algoritmo de Dekker V5** con dos hilos concurrentes, modelando exactamente el escenario de las dos sedes:

**Variables compartidas (en memoria):**

```java
private volatile boolean quiereEntrarSedeNorte = false;  // Bandera de intención Norte
private volatile boolean quiereEntrarSedeSur   = false;  // Bandera de intención Sur
private volatile int     turno = 1;                       // 1=Norte tiene prioridad, 2=Sur
private volatile int     inventarioSimulado = 1;          // El recurso compartido crítico
```

> La palabra clave `volatile` garantiza **visibilidad** entre hilos en la JVM: ningún hilo puede cachear el valor localmente; siempre lee desde la memoria principal.

### Diagrama del Algoritmo de Dekker V5

```
  HILO SEDE NORTE                          HILO SEDE SUR
  ─────────────────                        ──────────────────
  quiereEntrar = true                      quiereEntrar = true
        │                                        │
        ▼                                        ▼
  ¿quiereSur == true?──NO──┐         ┌──NO──¿quiereNorte == true?
        │YES                │         │          │YES
        ▼                   │         │          ▼
  ¿turno == 2?──NO──esperar │         │  esperar──NO──¿turno == 1?
        │YES                │         │                    │YES
        ▼                   │         │                    ▼
  quiereNorte = false       │         │         quiereSur = false
        │                   │         │                    │
  espera (turno≠2)          │         │         espera (turno≠1)
        │                   │         │                    │
  quiereNorte = true        │         │         quiereSur = true
        │                   │         │                    │
        └───────────────────┘         └────────────────────┘
                  │                                │
                  ▼                                ▼
         ╔═══════════════╗               ╔════════════════╗
         ║  SECCIÓN      ║               ║  SECCIÓN       ║
         ║  CRÍTICA      ║   (solo uno   ║  CRÍTICA       ║
         ║  NORTE        ║   a la vez)   ║  SUR           ║
         ╚═══════════════╝               ╚════════════════╝
                  │                                │
                  ▼                                ▼
         turno = 2                        turno = 1
         quiereNorte = false              quiereSur = false
```

### Propiedades Garantizadas

| Propiedad | Descripción | ¿Cumple? |
|-----------|-------------|----------|
| **Exclusión Mutua** | Nunca dos procesos en la sección crítica simultáneamente | ✅ |
| **Ausencia de Deadlock** | El sistema nunca queda bloqueado permanentemente | ✅ |
| **Ausencia de Starvation** | Ningún proceso espera indefinidamente gracias al turno | ✅ |
| **Sin Espera Activa Desenfrenada** | Cede el turno antes de re-intentar | ✅ |

### Cómo Probar la Simulación

```bash
# Invoca el endpoint de simulación de Dekker
GET http://localhost:8080/api/simulacion/dekker
```

**Respuesta esperada:**
```json
[
  "[SISTEMA] Iniciando Simulación: Algoritmo de Dekker (Versión 5) para 2 procesos.",
  "[SEDE NORTE] Iniciando intento de préstamo...",
  "[SEDE SUR] Iniciando intento de préstamo...",
  "[SEDE NORTE] Entró a la Sección Crítica (Exclusión Mutua garantizada).",
  "[SEDE NORTE] Préstamo exitoso. Inventario restante: 0",
  "[SEDE NORTE] Salió de la Sección Crítica y cedió el turno.",
  "[SEDE SUR] Entró a la Sección Crítica (Exclusión Mutua garantizada).",
  "[SEDE SUR] Fallo: Inventario agotado.",
  "[SEDE SUR] Salió de la Sección Crítica y cedió el turno.",
  "[SISTEMA] Simulación finalizada. Ningún proceso bloqueó al otro de forma permanente."
]
```

> Solo **una** sede obtiene el libro. La otra entra a la sección crítica pero encuentra el inventario en 0 — sin inconsistencia ni corrupción de datos.

---

## 🔄 Flujo Completo de un Préstamo

El siguiente diagrama describe el recorrido completo desde que el bibliotecario hace clic en "Solicitar Préstamo" hasta que el libro es entregado:

```
FRONTEND (React)          API GATEWAY           PRESTAMOS-SERVICE        PostgreSQL
─────────────────        ─────────────         ──────────────────        ──────────
     │                        │                        │                      │
     │ POST /api/prestamos     │                        │                      │
     │ /{libroId}?digital=X   │                        │                      │
     │  Headers:              │                        │                      │
     │   X-Sede: "Sede Norte" │                        │                      │
     │   X-Bibliotecario: ... │                        │                      │
     ├───────────────────────►│                        │                      │
     │                        │  Enrutamiento          │                      │
     │                        │  (round-robin Eureka)  │                      │
     │                        ├───────────────────────►│                      │
     │                        │                        │                      │
     │                        │                        │ SELECT * FROM libro  │
     │                        │                        │ WHERE id=?           │
     │                        │                        │ FOR UPDATE           │
     │                        │                        ├─────────────────────►│
     │                        │                        │◄─────────────────────┤
     │                        │                        │  (bloqueo adquirido) │
     │                        │                        │                      │
     │                        │           ┌────────────┴───────────┐         │
     │                        │           │   DECISIÓN DE STOCK    │         │
     │                        │           │                        │         │
     │                        │           │ ¿digital?              │         │
     │                        │           │   └─► ENTREGADO        │         │
     │                        │           │       (link digital)   │         │
     │                        │           │                        │         │
     │                        │           │ ¿stock local > 0?      │         │
     │                        │           │   └─► ENTREGADO local  │         │
     │                        │           │                        │         │
     │                        │           │ ¿stock otra sede > 0?  │         │
     │                        │           │   └─► PENDIENTE_ENVIO  │         │
     │                        │           │       (logística inter)│         │
     │                        │           │                        │         │
     │                        │           │ ¿sin stock?            │         │
     │                        │           │   └─► Error 503        │         │
     │                        │           └────────────┬───────────┘         │
     │                        │                        │                      │
     │                        │                        │ GET /api/time        │
     │                        │                        │ (Algoritmo Cristian) │
     │                        │◄───────────────────────┤                      │
     │                        ├───────────────────────►│                      │
     │                        │  serverTimeMs           │                      │
     │                        │                        │ Calcula:             │
     │                        │                        │  RTT = T1 - T0       │
     │                        │                        │  T_corregido =       │
     │                        │                        │  serverTime + RTT/2  │
     │                        │                        │                      │
     │                        │                        │ INSERT INTO prestamo │
     │                        │                        │ (con timestamp       │
     │                        │                        │  corregido)          │
     │                        │                        ├─────────────────────►│
     │                        │                        │◄─────────────────────┤
     │                        │                        │  (bloqueo liberado)  │
     │◄───────────────────────┤◄───────────────────────┤                      │
     │  200 OK: "Préstamo     │                        │                      │
     │  aprobado..."          │                        │                      │
```

### Estados del Ciclo de Vida de un Préstamo Inter-Sedes

```
                    ┌─────────────────────────────────────────────────┐
                    │           LIBRO SOLICITADO                       │
                    │     (stock local agotado, hay stock en otra sede)│
                    └─────────────────────┬───────────────────────────┘
                                          │
                                          ▼
                              ┌──────────────────────┐
                              │  PENDIENTE_DE_ENVIO   │
                              │  Acción: Sede origen  │
                              │  debe "Despachar"     │
                              └──────────┬───────────┘
                                         │ Bibliotecario de Sede Origen
                                         │ hace clic en "Despachar Envío"
                                         ▼
                              ┌──────────────────────┐
                              │     EN_TRANSITO       │
                              │  El libro está en     │
                              │  camino físicamente   │
                              └──────────┬───────────┘
                                         │ Bibliotecario de Sede Destino
                                         │ hace clic en "Entregar al Lector"
                                         ▼
                              ┌──────────────────────┐
                              │      ENTREGADO        │
                              │  Transacción cerrada  │
                              │  Pasa a historial     │
                              └──────────────────────┘
```

---

## 🕐 Sincronización de Relojes — Algoritmo de Cristian

En un sistema distribuido, cada nodo tiene su propio reloj físico que puede **derivar (drift)** con respecto al tiempo real. Si los préstamos se registran con timestamps incorrectos, el historial queda inconsistente.

### Implementación

Cada vez que se procesa un préstamo, el nodo ejecuta el **Algoritmo de Cristian**:

```
         NODO SEDE               API GATEWAY (Servidor de Tiempo)
         ─────────               ────────────────────────────────
              │                               │
    T0 = System.currentTimeMillis() + drift   │
              │                               │
              │──── GET /api/time ───────────►│
              │                               │
              │◄─── { serverTimeMs: T_s } ────│
              │                               │
    T1 = System.currentTimeMillis() + drift   │
              │                               │
    RTT = T1 - T0                             │
    T_corregido = T_s + (RTT / 2)             │
              │                               │
    Guarda en BD:                             │
      fechaSolicitud  = T_corregido  ← tiempo de referencia global
      fechaLocalSede  = T0           ← tiempo local de la sede (con drift)
      relojDriftMs    = drift configurado
      relojRttMs      = RTT medido
```

> La corrección `T_s + RTT/2` asume que el mensaje de respuesta tardó exactamente la mitad del viaje de ida y vuelta — compensando el desfase del reloj local.

### Variables de Configuración de Drift

En `docker-compose.yml`, la Sede Sur puede tener un drift simulado para pruebas:

```yaml
# prestamos-sur
environment:
  SPRING_PROFILES_ACTIVE: docker,sede-sur
  # En application-sede-sur.yml puede definirse: reloj.drift-ms: 3000
```

---

## 🚚 Logística Inter-Sedes

```
   SEDE NORTE                     SEDE SUR
   ──────────                     ─────────
   Solicita libro                       │
   (stock local = 0)                    │
   stock Sur > 0 ──────────────────────►│
   Estado: PENDIENTE_DE_ENVIO           │
              │                         │
              │        Bibliotecario Sur│
              │        ve tarea en panel│
              │        "Saliente (Despachar)"
              │                         │
              │        Clic "Despachar" │
              │◄────────────────────────┤
   Estado: EN_TRANSITO                  │
              │                         │
   Bibliotecario Norte                  │
   ve "Entrante (Recibir)"              │
   Clic "Entregar al Lector"            │
              │                         │
   Estado: ENTREGADO                    │
   Mueve a Historial                    │
```

---

## 🚀 Levantamiento del Sistema

### Pre-requisitos

- Docker Desktop instalado y en ejecución
- Git

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/JeremiAlex04/Libro-Net.git
cd Libro-Net

# 2. Copiar variables de entorno
cp .env.example .env

# 3. Levantar todos los servicios
docker-compose up --build -d

# 4. Verificar que todos los servicios están corriendo
docker-compose ps
```

### Verificación de Salud

| Servicio | URL | Descripción |
|----------|-----|-------------|
| Frontend | http://localhost:5173 | Interfaz de usuario |
| API Gateway | http://localhost:8080 | Punto de entrada REST |
| Eureka Dashboard | http://localhost:8761 | Panel de registro de servicios |
| PostgreSQL | localhost:5435 | Base de datos |

### Orden de Arranque

```
PostgreSQL ──► Eureka ──► Catálogo & Préstamos ──► Gateway ──► Frontend
```

> Los servicios tienen healthchecks configurados en `docker-compose.yml` para garantizar este orden.

---

## 🛠️ Endpoints de la API

Todos los endpoints son accesibles a través del API Gateway en `http://localhost:8080`.

### Autenticación
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Login de bibliotecario |

### Catálogo
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/catalogo/buscar?query={término}` | Buscar libros en el catálogo |

### Préstamos
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/prestamos/{libroId}?digital={bool}` | Solicitar préstamo (headers: `X-Sede`, `X-Bibliotecario`) |
| `GET` | `/api/prestamos` | Listar todos los préstamos |
| `PUT` | `/api/prestamos/{id}/estado?estado={estado}` | Actualizar estado logístico |

### Simulación & Utilidades
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/simulacion/dekker` | Ejecutar simulación del Algoritmo de Dekker V5 |
| `GET` | `/api/time` | Obtener tiempo de referencia del servidor (Algoritmo de Cristian) |

---

## 👥 Equipo

Proyecto desarrollado para el curso de **Sistemas Distribuidos** — Semana 13: Exclusión Mutua.

---

<div align="center">
<sub>LibroNet © 2025 — Sistema Distribuido de Gestión Bibliotecaria</sub>
</div>
