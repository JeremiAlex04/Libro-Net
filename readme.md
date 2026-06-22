# LibroNet — Sistema Distribuido de Gestión Bibliotecaria

> **Semana 13 · Sistemas Distribuidos**  
> Concurrencia y Exclusión Mutua aplicadas a un sistema de préstamos multi-sede en tiempo real.

LibroNet es una arquitectura de microservicios que gestiona el inventario físico y digital de libros entre dos sedes bibliotecarias (**Sede Norte** y **Sede Sur**), operando bajo principios de **Exclusión Mutua**, **Algoritmo de Cristian** y **consistencia fuerte (CP del Teorema CAP)**.

---

## Índice

1. [Arquitectura General](#1-arquitectura-general)
2. [Concurrencia y Exclusión Mutua](#2-concurrencia-y-exclusión-mutua)
3. [Inventario por Sede y Copias Digitales](#3-inventario-por-sede-y-copias-digitales)
4. [Sincronización de Tiempo — Algoritmo de Cristian](#4-sincronización-de-tiempo--algoritmo-de-cristian)
5. [Interfaz del Bibliotecario](#5-interfaz-del-bibliotecario)
6. [Flujo de Préstamo Interbibliotecario](#6-flujo-de-préstamo-interbibliotecario)
7. [Escalamiento hacia N Sedes](#7-escalamiento-hacia-n-sedes)
8. [Despliegue con Docker](#8-despliegue-con-docker)
9. [Credenciales y Acceso](#9-credenciales-y-acceso)
10. [Protocolo de Pruebas](#10-protocolo-de-pruebas)
11. [Estructura del Proyecto](#11-estructura-del-proyecto)

---

## 1. Arquitectura General

```
[ Cliente Web: React + Bootstrap 5 ]  ← http://localhost:5173
              │
              ▼  /api/*
[ API Gateway: Spring Cloud Gateway ]  ← http://localhost:8080
    │   (+ Servidor de Tiempo /api/time)
    │                    ▲ Registro de servicios
    ▼                    ▼
[ Eureka Server ]  ← http://localhost:8761
    │
    ├──────────────────────────────────────┐
    ▼                                      ▼
[ catalogo-service ]            [ prestamos-service ]
  Búsquedas (read-only)           Transacciones + Exclusión Mutua
  Puerto: 8082                    Sede Norte: 8081
                                  Sede Sur:   8083
              │                              │
              └──────────────┬───────────────┘
                             ▼
               [ PostgreSQL: biblioteca_db ]
               Puerto: 5435 (host) / 5432 (interno)
               TZ: America/Lima (UTC-5)
```

### Servicios registrados en Eureka

| Nombre Eureka             | Rol                              | Puerto |
|---------------------------|----------------------------------|--------|
| `demo-eureka-server`      | Directorio de descubrimiento     | 8761   |
| `libronet-api-gateway`    | Gateway + Servidor de tiempo     | 8080   |
| `libronet-catalogo`       | Catálogo de libros (solo lectura)| 8082   |
| `libronet-prestamos`      | Préstamos — Sede Norte           | 8081   |
| `libronet-prestamos`      | Préstamos — Sede Sur             | 8083   |

---

## 2. Concurrencia y Exclusión Mutua

### La Sección Crítica

La **Sección Crítica** de LibroNet es el bloque que modifica el inventario de un libro (`copias_norte` / `copias_sur`) y registra el préstamo en la base de datos. Sin control de concurrencia, dos sedes podrían prestar el mismo ejemplar físico simultáneamente.

**Escenario de riesgo sin exclusión mutua:**

```
Tiempo   Nodo Norte (T1)           Nodo Sur (T2)
──────   ────────────────────────  ────────────────────────
 t=0     Lee: copias_norte = 1     Lee: copias_norte = 1
 t=1     Evalúa: 1 > 0  ✓          Evalúa: 1 > 0  ✓
 t=2     Escribe: copias_norte = 0  Escribe: copias_norte = 0
         ↑ ¡Ambas reclaman el mismo ejemplar! Stock corrupto.
```

### Implementación: Bloqueo Pesimista (`SELECT FOR UPDATE`)

La exclusión mutua se implementa en `PrestamoService.java` mediante `@Transactional` + `findByIdForUpdate()`:

```java
// LibroRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT l FROM Libro l WHERE l.id = :id")
Optional<Libro> findByIdForUpdate(@Param("id") UUID id);
```

```java
// PrestamoService.java
@Transactional
public String procesarPrestamo(UUID libroId, String sede,
                                String bibliotecario, boolean digital) {
    // ── INICIO SECCIÓN CRÍTICA ──────────────────────────────
    Libro libro = libroRepository.findByIdForUpdate(libroId)
            .orElseThrow(() -> new RuntimeException("Libro no encontrado"));

    // Evaluación del recurso compartido bajo bloqueo exclusivo
    boolean isNorte  = sede.equalsIgnoreCase("Sede Norte");
    int localStock   = isNorte ? libro.getCopiasNorte() : libro.getCopiasSur();
    int otherStock   = isNorte ? libro.getCopiasSur()   : libro.getCopiasNorte();

    if (localStock > 0) {
        // Préstamo local — decrementa stock de la sede actual
        if (isNorte) libro.setCopiasNorte(libro.getCopiasNorte() - 1);
        else         libro.setCopiasSur(libro.getCopiasSur() - 1);
        estadoFinal = EstadoPrestamo.ENTREGADO;
    } else if (otherStock > 0) {
        // Interbibliotecario — decrementa stock de la otra sede
        if (isNorte) libro.setCopiasSur(libro.getCopiasSur() - 1);
        else         libro.setCopiasNorte(libro.getCopiasNorte() - 1);
        estadoFinal = EstadoPrestamo.PENDIENTE_DE_ENVIO;
    } else {
        throw new RuntimeException("Sin stock físico disponible.");
    }
    libroRepository.save(libro);
    // ── FIN SECCIÓN CRÍTICA (COMMIT libera el bloqueo) ──────
}
```

### Equivalencia con el Algoritmo de Dekker V5

El bloqueo pesimista de PostgreSQL implementa los mismos principios que Dekker V5:

| Dekker V5 (Conceptual)           | Implementación en LibroNet               |
|----------------------------------|------------------------------------------|
| `flag[i] = true` (intención)     | `BEGIN TRANSACTION`                      |
| Espera activa si `flag[j] = true`| `SELECT FOR UPDATE` (PostgreSQL suspende)|
| Variable `turn` rompe empates    | Cola FIFO interna de PostgreSQL          |
| Sección Crítica                  | Lectura + Modificación del inventario    |
| `flag[i] = false` (liberación)   | `COMMIT`                                 |

**¿Por qué Dekker V5 y no versiones anteriores?**

| Versión | Fallo                    | Consecuencia en LibroNet                      |
|---------|--------------------------|-----------------------------------------------|
| V1      | Alternancia estricta     | Norte espera a Sur aunque el libro esté libre |
| V2      | Interbloqueo             | Ambas sedes quedan bloqueadas mutuamente      |
| V3      | Postergación indefinida  | Una sede espera eternamente                   |
| V4      | Condición de carrera     | Dos sedes creen tener acceso simultáneo       |
| **V5**  | **Sin deficiencias**     | Combina banderas + variable de turno          |

---

## 3. Inventario por Sede y Copias Digitales

### Esquema de Base de Datos

```sql
CREATE TABLE libro (
    id           UUID PRIMARY KEY,
    titulo       VARCHAR(255),
    copias_norte INT NOT NULL DEFAULT 0,   -- Stock físico en Sede Norte
    copias_sur   INT NOT NULL DEFAULT 0,   -- Stock físico en Sede Sur
    url_digital  VARCHAR(500)              -- Enlace a E-Book (opcional)
);
```

### Libros Sembrados

| Título                        | Norte | Sur | Digital |
|-------------------------------|-------|-----|---------|
| El Arte de la Escalabilidad   |   2   |  1  |   ✅    |
| Sistemas Distribuidos         |   0   |  2  |   ✅    |
| Tradiciones Peruanas          |   3   |  2  |   ✅    |
| La Ciudad y los Perros        |   4   |  3  |   ✅    |
| Conversación en La Catedral   |   2   |  2  |   ✅    |
| El Sexto                      |   3   |  1  |   ✅    |
| Yawar Fiesta                  |   2   |  4  |   ✅    |
| Los Ríos Profundos            |   5   |  2  |   ✅    |
| Redoble por Rancas            |   1   |  3  |   ✅    |
| País de Jauja                 |   2   |  2  |   ✅    |
| No me Esperen en Abril        |   4   |  1  |   ✅    |
| La Palabra del Mudo           |   3   |  3  |   ✅    |

### Lógica de Decisión de Préstamo

```
¿Préstamo Digital?
  └─ SÍ → Aprobado inmediatamente (ENTREGADO). Sin afectar stock físico.
  └─ NO → Evalúa stock de la sede solicitante:
        ├─ localStock > 0  → ENTREGADO (préstamo local inmediato)
        ├─ localStock = 0 y otherStock > 0 → PENDIENTE_DE_ENVIO (interbibliotecario)
        └─ Ambos = 0 → Rechazado ("Sin stock físico disponible")
```

---

## 4. Sincronización de Tiempo — Algoritmo de Cristian

Antes de registrar cada préstamo, el nodo de préstamos sincroniza su reloj con el API Gateway:

```
Nodo Préstamos                     API Gateway (/api/time)
──────────────────                 ──────────────────────
t₀ = reloj_local + drift
        ─── GET /api/time ────────────────────────────►
                                   { serverTimeMs: T_srv }
        ◄── T_srv ───────────────────────────────────
t₁ = reloj_local + drift

RTT          = t₁ - t₀
T_corregido  = T_srv + (RTT / 2)

fechaSolicitud = LocalDateTime.ofInstant(
    Instant.ofEpochMilli(T_corregido),
    ZoneId.of("America/Lima")          // UTC-5
)
```

Cada registro de préstamo almacena:

| Campo            | Descripción                                        |
|------------------|----------------------------------------------------|
| `fecha_solicitud`| Hora corregida por Cristian (referencia oficial)   |
| `fecha_local_sede`| Hora del reloj local de la sede antes de corregir|
| `reloj_drift_ms` | Desfase configurado del nodo (en ms)               |
| `reloj_rtt_ms`   | Round-Trip Time de la consulta al servidor de tiempo|

> El **Modo Auditoría** en la interfaz expone estos datos técnicos por préstamo mediante un panel expandible `[+ Ver Sync]`.

---

## 5. Interfaz del Bibliotecario

### Login

El sistema autentica al bibliotecario verificando `username + password + sede` contra la tabla `bibliotecario`. Una combinación incorrecta de sede impide el acceso aunque las credenciales sean válidas.

### Catálogo de Libros

Cada tarjeta de libro muestra:
- **Badge verde** `Local (N)`: copias disponibles en la sede actual.
- **Badge azul/rojo** `Sede Sur/Norte (N)`: copias en la otra sede.
- Botones de acción condicionales según disponibilidad (ver sección 6).

### Panel de Logística (pestañas)

| Pestaña               | Contenido                                                       |
|-----------------------|-----------------------------------------------------------------|
| **Logística Activa**  | Envíos interbibliotecarios pendientes filtrados por sede activa |
| **Historial de Préstamos** | Todos los préstamos completados (`ENTREGADO`)            |

**Roles de cada sede en Logística Activa:**

| Estado del Envío        | Vista Sede Solicitante        | Vista Sede Origen              |
|-------------------------|-------------------------------|--------------------------------|
| `PENDIENTE_DE_ENVIO`    | "Entrante (Espera)"           | "Saliente (Despachar)" + botón |
| `EN_TRANSITO`           | "Entrante (Recibir)" + botón  | "Saliente (En camino)"         |
| `ENTREGADO`             | Aparece en Historial          | Aparece en Historial           |

---

## 6. Flujo de Préstamo Interbibliotecario

El proceso implementa un flujo de **dos fases** para evitar reservas accidentales sobre el stock de otra sede:

### Fase 1 — Consulta de disponibilidad (sin transacción)
```
Bibliotecario Norte ve: "Sistemas Distribuidos — Local (0) | Sede Sur (2)"
→ Clic en: [ 🔍 Consultar Disponibilidad en Sede Sur ]
→ Se expande panel informativo:
   "📦 Sede Sur tiene 2 copias disponibles para envío físico.
    Al confirmar, se reservará una copia y quedará pendiente de despacho."
```

### Fase 2 — Confirmación y transacción
```
→ Clic en: [ 🚚 Confirmar Envío desde Sede Sur ]
→ POST /api/prestamos/{id}?digital=false  (X-Sede: Sede Norte)
→ Sección Crítica ejecutada: copias_sur - 1, estado = PENDIENTE_DE_ENVIO
→ Aparece en Logística Activa de ambas sedes
```

### Diagrama de Secuencia Completo

```
Bibliot.Norte   Frontend   Gateway   Nodo Prestamos   PostgreSQL   Bibliot.Sur
     │              │          │            │               │            │
     │─Clic Fase 1─►│          │            │               │            │
     │◄─Panel info──│          │            │               │            │
     │─Clic Fase 2─►│          │            │               │            │
     │              │─POST────►│            │               │            │
     │              │          │─enruta────►│               │            │
     │              │          │            │─BEGIN TX──────►            │
     │              │          │            │─SELECT FOR UPD►            │
     │              │          │            │◄─fila bloqueada─           │
     │              │          │            │─UPDATE copias──►            │
     │              │          │            │─INSERT prestamo►            │
     │              │          │            │─COMMIT (unlock)►            │
     │              │◄─200 OK──│◄──────────►│               │            │
     │◄─Alerta éxito─│          │            │               │            │
     │              │          │            │               │            │
     │              │          │            │          (polling 5s)       │
     │              │          │            │               │─Ver Logíst.►│
     │              │          │            │               │◄─"Despachar"│
     │              │          │            │               │─PUT EN_TRANS►
     │◄─Ver "Recibir"─          │            │               │            │
     │─Clic Entregar►           │─PUT ENTREGADO──────────────►            │
```

---

## 7. Escalamiento hacia N Sedes

### Opción A: Token Ring (redes estables)

Una sede solo puede escribir en la base de datos cuando posee el **token**. El token circula en anillo entre todas las sedes.

```
Sede Norte ──token──► Sede Sur ──token──► Sede Este ──token──► Sede Oeste ──┐
     ▲                                                                        │
     └────────────────────────────────────────────────────────────────────────┘
```

- **Ventaja:** Libre de interbloqueos por diseño.
- **Requerimiento:** Protocolo de recuperación si el nodo con el token falla.

### Opción B: Basado en Permisos (Ricart-Agrawala)

La sede solicitante debe obtener permiso de **todas** las demás antes de entrar a la Sección Crítica.

- **Mensajes requeridos:** `2(N-1)` por operación.
- **Ventaja:** No requiere coordinador central.
- **Consideración:** A mayor N, mayor tráfico de red.

### Comparativa

| Estrategia              | Mensajes/op.  | Tolerancia a fallos | Caso de uso              |
|-------------------------|---------------|---------------------|--------------------------|
| `SELECT FOR UPDATE`     | 0 (DB local)  | Alta (Postgres HA)  | 2–5 sedes, 1 DB central  |
| Token Ring              | 1             | Media               | 5–20 sedes, red estable  |
| Ricart-Agrawala         | 2(N-1)        | Alta                | +20 sedes, red dinámica  |

---

## 8. Despliegue con Docker

### Requisitos

- Docker Desktop (o Docker Engine + Compose v2)

### Arranque rápido

```bash
# Construir y levantar todos los servicios
docker compose up -d --build

# Ver logs en tiempo real
docker compose logs -f gateway

# Detener y eliminar contenedores (preserva datos)
docker compose down

# Detener y borrar volumen (reinicia base de datos y libros)
docker compose down -v
```

### URLs expuestas

| Servicio   | URL                       | Credenciales             |
|------------|---------------------------|--------------------------|
| Frontend   | http://localhost:5173     | Ver sección 9            |
| Gateway    | http://localhost:8080     | —                        |
| Eureka     | http://localhost:8761     | —                        |
| PostgreSQL | localhost:**5435**        | postgres / 200319        |

> **Nota:** PostgreSQL se expone en el puerto `5435` del host (no `5432`) para evitar conflictos con instalaciones locales.

### Variables de entorno (opcional)

```bash
cp .env.example .env
# Editar POSTGRES_USER y POSTGRES_PASSWORD según sea necesario
```

### Zona Horaria

Todos los servicios operan en `TZ=America/Lima` (UTC-5). Las fechas de préstamo se almacenan y muestran en hora peruana.

---

## 9. Credenciales y Acceso

| Sede        | Usuario       | Contraseña | Rol           |
|-------------|---------------|------------|---------------|
| Sede Norte  | `admin_norte` | `norte123` | Bibliotecario |
| Sede Sur    | `admin_sur`   | `sur123`   | Bibliotecario |

> El sistema valida que el usuario pertenezca a la sede seleccionada en el login. Un `admin_norte` no puede iniciar sesión eligiendo "Sede Sur".

---

## 10. Protocolo de Pruebas

### Prueba A: Exclusión Mutua

1. Asegúrate de que un libro tenga exactamente **1 copia** en Sede Norte.
2. Abre dos pestañas del navegador, ambas con sesión de `admin_norte`.
3. Solicita el préstamo en ambas pestañas al mismo tiempo.
4. **Resultado esperado:** una transacción obtiene `ENTREGADO`; la otra recibe `"Sin stock físico disponible"`. El stock queda en 0, no en -1.

### Prueba B: Flujo Interbibliotecario Completo

1. Login como `admin_norte` → "Sistemas Distribuidos" tiene Local (0), Sede Sur (2).
2. Clic **"Consultar Disponibilidad en Sede Sur"** → aparece el panel informativo.
3. Clic **"Confirmar Envío desde Sede Sur"** → estado `PENDIENTE_DE_ENVIO` en Logística Activa de Norte ("Entrante — Espera").
4. Login como `admin_sur` → aparece "Saliente (Despachar)" → clic **"Despachar Envío"** → `EN_TRANSITO`.
5. Login como `admin_norte` → aparece "Entrante (Recibir)" → clic **"Entregar al Lector"** → `ENTREGADO`.

### Prueba C: Préstamo Digital

1. Para cualquier libro, clic en **"Derivar Copia Digital (E-Book)"**.
2. **Resultado esperado:** transacción `ENTREGADO` inmediata. El stock físico (`copias_norte` y `copias_sur`) permanece sin cambios.

### Prueba D: Modo Auditoría (Cristian Sync)

1. Activa el toggle **"Modo Auditoría"** en la barra de navegación.
2. En la pestaña "Historial de Préstamos", aparece el enlace `[+ Ver Sync]` en cada fila.
3. Al hacer clic, se despliega: hora corregida por Cristian, hora local de la sede, Drift (ms) y RTT (ms).

### Prueba E: Tolerancia a Fallos

1. Detén el contenedor de préstamos: `docker stop libronet-prestamos-norte`.
2. Intenta un préstamo desde la interfaz.
3. **Resultado esperado:** alerta `"Error Crítico: El servicio central no responde"` sin colapsar la UI. El Gateway intenta el nodo Sur como fallback.

---

## 11. Estructura del Proyecto

```
bibliotecaDistribuido/
├── docker-compose.yml                     # Orquestación de todos los servicios
├── docker/
│   └── postgres/
│       └── init.sql                       # Esquema + datos semilla (12 libros)
│
├── red/                                   # Servidor Eureka (descubrimiento)
├── api-gateway/                           # Spring Cloud Gateway + /api/time
│   └── src/.../TimeController.java        # Servidor de referencia temporal (Cristian)
│
├── catalogo-service/                      # Búsqueda de libros (read-only)
│   └── src/.../model/Libro.java           # Entidad: copiasNorte, copiasSur, urlDigital
│
├── prestamos-service/                     # Motor de préstamos + Exclusión Mutua
│   └── src/.../
│       ├── model/
│       │   ├── Libro.java                 # Entidad con inventario por sede
│       │   ├── Prestamo.java              # Registro de préstamo (+ campos Cristian)
│       │   └── EstadoPrestamo.java        # ENTREGADO | PENDIENTE_DE_ENVIO | EN_TRANSITO
│       ├── repository/
│       │   └── LibroRepository.java       # findByIdForUpdate() → SELECT FOR UPDATE
│       ├── service/
│       │   └── PrestamoService.java       # Sección Crítica + Algoritmo de Cristian
│       └── controller/
│           └── PrestamoController.java    # POST /{id}?digital= | PUT /{id}/estado
│
└── _frontend-libronet/                    # Cliente React + Vite + Bootstrap 5
    └── src/
        ├── App.jsx                        # Estado global + pestañas logística/historial
        └── components/
            ├── Login.jsx                  # Autenticación por sede
            ├── Navbar.jsx                 # Toggle Modo Auditoría
            └── BookCard.jsx               # Flujo 2 fases (consulta → confirmación)
```

---

## Referencias Técnicas

| Tecnología              | Versión  | Rol en el sistema                    |
|-------------------------|----------|--------------------------------------|
| Java Spring Boot        | 3.x      | Backend de microservicios            |
| Spring Data JPA         | 3.x      | ORM + `@Lock(PESSIMISTIC_WRITE)`     |
| Spring Cloud Gateway    | 4.x      | Enrutamiento + balanceo de carga     |
| Netflix Eureka          | —        | Descubrimiento de servicios          |
| PostgreSQL              | 16       | Base de datos centralizada (CP)      |
| React + Vite            | 18 / 8   | Interfaz SPA del bibliotecario       |
| Bootstrap 5             | 5.3      | Estilos e iconografía (`bi-*`)       |
| Docker Compose          | v2       | Orquestación del stack completo      |

---

*Proyecto académico — Semana 13: Concurrencia y Exclusión Mutua*  
*Repositorio: `JeremiAlex04/Libro-Net`*
