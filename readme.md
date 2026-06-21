# LibroNet: Sistema Distribuido de Préstamos Bibliotecarios

Este repositorio contiene la implementación de **LibroNet**, una arquitectura de microservicios para gestionar búsqueda, préstamo y disponibilidad concurrente de libros entre múltiples sedes bibliotecarias. El sistema utiliza **Java Spring Boot**, **React JS**, **PostgreSQL** y **Netflix Eureka**, priorizando **consistencia fuerte (enfoque CP del Teorema CAP)** y degradación elegante ante fallos.

---

## Arquitectura de la Red

```text
[ Cliente Web: React + Bootstrap ] (Puerto 5173)
         │
         ▼ (HTTP /api/*)
[ API Gateway: Spring Cloud Gateway ] (Puerto 8080) ◄───► [ Eureka Server ] (Puerto 8761)
         │
         ├──────────────────────────────┐
         ▼                              ▼
[ catalogo-service ]              [ prestamos-service ]
(Búsquedas — Puerto 8082)         (Transacciones — Puertos 8081 y 8083)
         │                              │
         └──────────────┬───────────────┘
                        ▼
              [ PostgreSQL: biblioteca_db ] (Puerto 5432)
```

### Servicios registrados en Eureka

| Servicio lógico        | Nombre Eureka           | Puerto(s) |
|------------------------|-------------------------|-----------|
| Directorio de nombres  | `demo-eureka-server`    | 8761      |
| Puerta de entrada      | `libronet-api-gateway`  | 8080      |
| Catálogo (solo lectura)| `libronet-catalogo`     | 8082      |
| Préstamos (transacciones) | `libronet-prestamos` | 8081, 8083 |

El Gateway enruta con balanceo de carga (`lb://`):

- `/api/catalogo/**` → `libronet-catalogo`
- `/api/prestamos/**` → `libronet-prestamos` (round-robin entre nodos)
- `/api/simulacion/**` → `libronet-prestamos` (módulo académico Dekker)

---

## Estructura del Proyecto

```text
bibliotecaDistribuido/
├── red/                               # Servidor Eureka (antes backend/demo)
├── api-gateway/                       # Spring Cloud Gateway
├── catalogo-service/                  # Búsquedas en catálogo
├── prestamos-service/                 # Préstamos + simulación Dekker
│   └── src/main/resources/
│       ├── application.yml            # Sede Norte (puerto 8081)
│       └── application-sede-sur.yml   # Sede Sur (puerto 8083)
└── _frontend-libronet/                # Cliente React + Vite + Bootstrap 5
```

---

## Fundamentos Distribuidos

### Nombramiento e identificadores

Los libros usan **UUID** inmutables. Los microservicios se registran con nombres lógicos en Eureka; el Gateway resuelve direcciones físicas dinámicamente.

### Exclusión mutua

Se descarta Lamport/Dekker para transacciones reales (HTTP sin memoria compartida). La consistencia se garantiza con **bloqueos pesimistas** (`PESSIMISTIC_WRITE` / `SELECT FOR UPDATE`) en PostgreSQL.

### Multi-nodos por sede

Dos instancias del mismo microservicio (`libronet-prestamos`) pueden ejecutarse en paralelo:

- **Sede Norte:** puerto 8081 (perfil por defecto)
- **Sede Sur:** puerto 8083 (perfil `sede-sur`)

El Gateway distribuye peticiones entre ambas instancias. La respuesta del backend incluye el **nodo que atendió** (`Atendido en nodo 8081` o `8083`) junto con los headers `X-Sede` y `X-Bibliotecario` enviados por el frontend.

### Tolerancia a fallos

El frontend captura errores de red con `try/catch` y muestra alertas controladas sin colapsar la aplicación.

---

## Requisitos Previos

- JDK 17
- Apache Maven
- Node.js 18+ y npm
- PostgreSQL activo en el puerto 5432

---

## Configuración de la Base de Datos

```sql
CREATE DATABASE biblioteca_db;

\c biblioteca_db;

CREATE TABLE IF NOT EXISTS libro (
    id UUID PRIMARY KEY,
    titulo VARCHAR(255),
    copias_disponibles INT
);

INSERT INTO libro (id, titulo, copias_disponibles)
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'El Arte de la Escalabilidad', 3)
ON CONFLICT (id) DO NOTHING;
```

Ajusta usuario y contraseña en `catalogo-service` y `prestamos-service` (`application.yml`) según tu instalación local.

---

## Despliegue Local (orden de arranque)

Abre **seis terminales** independientes:

### 1. Eureka Server

```bash
cd red
mvn spring-boot:run
```

Verificación: `http://localhost:8761`

### 2. API Gateway

```bash
cd api-gateway
mvn spring-boot:run
```

Verificación: instancia `LIBRONET-API-GATEWAY` en Eureka.

### 3. Servicio de Catálogo

```bash
cd catalogo-service
mvn spring-boot:run
```

Verificación: instancia `LIBRONET-CATALOGO` en Eureka.

### 4. Préstamos — Sede Norte (puerto 8081)

```bash
cd prestamos-service
mvn spring-boot:run
```

### 5. Préstamos — Sede Sur (puerto 8083)

```bash
cd prestamos-service
mvn spring-boot:run -Dspring-boot.run.profiles=sede-sur
```

En PowerShell (Windows):

```powershell
cd prestamos-service
mvn spring-boot:run "-Dspring-boot.run.profiles=sede-sur"
```

Verificación: en Eureka deben aparecer **dos instancias** de `LIBRONET-PRESTAMOS` (8081 y 8083).

### 6. Frontend

```bash
cd _frontend-libronet
npm install
npm run dev
```

Verificación: `http://localhost:5173`

---

## Uso de la Interfaz

1. Inicia sesión como bibliotecario y selecciona la sede (Norte o Sur).
2. Busca libros en el catálogo global (case-insensitive).
3. Aprueba un préstamo; la respuesta incluye UUID, nodo atendido, bibliotecario y sede.
4. Opcional: ejecuta la **Simulación Dekker V5** desde el panel inferior (módulo académico vía Gateway).

---

## Protocolo de Pruebas

### Prueba A: Exclusión mutua

1. Deja un libro con **1 copia** disponible en PostgreSQL.
2. Abre dos ventanas del navegador y solicita el préstamo simultáneamente.
3. **Resultado esperado:** una ventana obtiene éxito; la otra, denegación. En consola del servicio verás `SELECT ... FOR UPDATE`.

### Prueba B: Balanceo multi-nodo

1. Arranca ambas instancias de `prestamos-service` (8081 y 8083).
2. Realiza varios préstamos desde el frontend.
3. **Resultado esperado:** las respuestas alternan el campo `Atendido en nodo 8081` / `8083` según el balanceo del Gateway.

### Prueba C: Tolerancia a fallos

1. Detén `prestamos-service` con `Ctrl+C`.
2. Intenta un préstamo desde el navegador.
3. **Resultado esperado:** alerta *"Error Crítico: El servicio central no responde"* sin colapsar la UI.

### Prueba D: Simulación Dekker

1. Con el backend activo, pulsa **Ejecutar Dekker V5** en el frontend.
2. **Resultado esperado:** bitácora de eventos de exclusión mutua simulada en memoria JVM.

---

## Migración a IntelliJ IDEA

| Componente   | Directorio            | URL                    |
|--------------|-----------------------|------------------------|
| Eureka       | `red/`                | http://localhost:8761  |
| Gateway      | `api-gateway/`        | http://localhost:8080  |
| Catálogo     | `catalogo-service/`   | http://localhost:8082  |
| Préstamos N. | `prestamos-service/`  | http://localhost:8081  |
| Préstamos S. | `prestamos-service/` (perfil `sede-sur`) | http://localhost:8083 |
| Frontend     | `_frontend-libronet/` | http://localhost:5173  |

Para la segunda instancia de préstamos en IntelliJ, añade **Active profiles:** `sede-sur` en la configuración de ejecución.

---

## Despliegue con Docker

### Requisitos

- Docker Desktop (o Docker Engine + Docker Compose v2)

### Arranque rápido

Desde la raíz del repositorio:

```bash
docker compose up --build
```

En PowerShell (Windows):

```powershell
docker compose up --build
```

La primera construcción puede tardar varios minutos (descarga de imágenes Maven/Node y compilación).

### URLs expuestas

| Servicio   | URL                         |
|------------|-----------------------------|
| Frontend   | http://localhost:5173       |
| Gateway    | http://localhost:8080       |
| Eureka     | http://localhost:8761       |
| PostgreSQL | localhost:5432              |

### Servicios en la red Docker

```text
postgres → eureka → catalogo + prestamos-norte + prestamos-sur → gateway → frontend
```

- **Perfil `docker`:** reemplaza `localhost` por nombres de servicio (`postgres`, `eureka`).
- **Dos nodos de préstamos:** `prestamos-norte` (8081) y `prestamos-sur` (8083, perfil `sede-sur`).
- **Datos iniciales:** `docker/postgres/init.sql` inserta libros de prueba al crear el volumen.

### Variables de entorno (opcional)

Copia `.env.example` a `.env` para cambiar credenciales de PostgreSQL:

```bash
cp .env.example .env
```

### Comandos útiles

```bash
# Segundo plano
docker compose up -d --build

# Ver logs
docker compose logs -f gateway

# Detener y eliminar contenedores
docker compose down

# Detener y borrar volumen de BD (reinicia datos)
docker compose down -v
```

### Estructura Docker añadida

```text
docker-compose.yml
docker/postgres/init.sql
.env.example
red/Dockerfile
api-gateway/Dockerfile
catalogo-service/Dockerfile
prestamos-service/Dockerfile
_frontend-libronet/Dockerfile
_frontend-libronet/nginx.conf
*/src/main/resources/application-docker.yml   # perfiles Docker en cada servicio Java
```

### Nota sobre puertos locales

Si ya tienes PostgreSQL o algún servicio en los puertos 5432, 8080, 8761 o 5173, detén esos procesos antes de levantar Docker o edita los mapeos en `docker-compose.yml`.

