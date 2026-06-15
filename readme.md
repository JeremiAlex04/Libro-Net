# LibroNet: Sistema Distribuido de Préstamos Bibliotecarios

Este repositorio contiene la implementación física de **LibroNet**, una arquitectura de sistemas distribuidos diseñada para gestionar la búsqueda, préstamo y disponibilidad concurrente de libros entre múltiples sedes bibliotecarias. El sistema reemplaza los modelos teóricos clásicos por componentes modernos de nivel de producción bajo el ecosistema de **Java Spring Boot**, **React JS** y **PostgreSQL**, garantizando **Consistencia Fuerte (enfoque CP del Teorema CAP)** y tolerancia a fallos.

---

## 🏛️ Arquitectura de la Red

El sistema está estructurado bajo un patrón de microservicios desacoplados que se comunican mediante paso de mensajes a través de la red local, eliminando puntos únicos de falla (SPOF) y asegurando la escalabilidad horizontal.

```text
[ Cliente Web: React ] (Puerto 5173)
         │
         ▼ (Petición HTTP POST / JSON)
[ API Gateway: Spring Cloud Gateway ] (Puerto 8080) ◄───► [ Directorio: Netflix Eureka ] (Puerto 8761)
         │
         ▼ (Enrutamiento Dinámico)
[ Microservicio de Préstamos ] (Puerto 8081)
         │
         ▼ (Bloqueo Pesimista / Transacción ACID)
[ Base de Datos: PostgreSQL ] (Puerto 5432)
```

### Flujo de Trabajo y Enrutamiento Dinámico (UML Sequence)

```text
[React JS]           [API Gateway]          [Eureka Server]        [Prestamos Service]       [PostgreSQL]
(Puerto 5173)        (Puerto 8080)          (Puerto 8761)          (Puerto 8081)             (Puerto 5432)
      │                    │                       │                       │                       │
      │ 1. POST /api/...   │                       │                       │                       │
      ├───────────────────►│                       │                       │                       │
      │                    │ 2. ¿Dónde está 'libronet-prestamos'?          │                       │
      │                    ├──────────────────────►│                       │                       │
      │                    │                       │                       │                       │
      │                    │ 3. Responde: localhost:8081                   │                       │
      │                    │◄──────────────────────┤                       │                       │
      │                    │                       │                       │                       │
      │                    │ 4. Reenvía POST original                      │                       │
      │                    ├──────────────────────────────────────────────►│                       │
      │                    │                       │                       │ 5. SELECT FOR UPDATE  │
      │                    │                       │                       ├──────────────────────►│
      │                    │                       │                       │                       │
      │                    │                       │                       │    (Bloqueo Físico)   │
      │                    │                       │                       │                       │
      │                    │                       │                       │ 6. Confirma descuento │
      │                    │                       │                       │◄──────────────────────┤
      │                    │                       │                       │                       │
      │                    │ 7. Responde HTTP 200 OK                       │                       │
      │                    │◄──────────────────────────────────────────────┤                       │
      │                    │                       │                       │                       │
      │ 8. Muestra Éxito   │                       │                       │                       │
      │◄───────────────────┤                       │                       │                       │
```

---

## 🛠️ Fundamentos Distribuidos Implementados

### 1. Nombramiento e Identificadores (Resolución de Nombres)

* **Concepto Teórico:** Separación estricta entre nombres (legibles por humanos), identificadores (inmutables y persistentes) y direcciones (puntos de acceso IP/Puerto).
* **Implementación Física:** Los libros se identifican mediante llaves primarias de tipo `UUID` o `ISBN` inmutables. La resolución de nombres jerárquicos se delega a **Netflix Eureka Server**. Los microservicios no exponen direcciones IP estáticas; en su lugar, se registran con un identificador lógico (`libronet-prestamos`). El **API Gateway** intercepta las llamadas del frontend y resuelve dinámicamente la ubicación física consultando al directorio.

### 2. Exclusión Mutua y Consistencia Fuerte

* **Concepto Teórico:** Prevención de colisiones e inconsistencias ante peticiones concurrentes masivas sobre el mismo recurso físico.
* **Implementación Física:** Dado que la red opera mediante paso de mensajes y no comparte memoria RAM, se descarta el uso del Algoritmo de la Panadería de Lamport. La exclusión mutua se resuelve en la capa de persistencia mediante **Bloqueos Pesimistas (`PESSIMISTIC_WRITE`)** en PostgreSQL. Al iniciar el procesamiento de un préstamo, el backend ejecuta una consulta nativa `SELECT ... FOR UPDATE`, bloqueando la fila correspondiente a nivel de motor. Cualquier petición concurrente de otra sede es retenida en una cola secuencial hasta el cierre de la transacción, garantizando que el inventario jamás descienda de cero.

### 3. Sincronización de Tiempo y Ordenamiento Causal

* **Concepto Teórico:** Mantenimiento de un orden cronológico absoluto de eventos en ausencia de un reloj global compartido.
* **Implementación Física:** Se prescinde de algoritmos de software manuales (Cristian o Berkeley) en la capa de aplicación. La sincronización del tiempo físico se delega al protocolo de infraestructura **NTP (Network Time Protocol)** activo en los sistemas operativos de los servidores. El ordenamiento de las transacciones críticas queda garantizado de forma determinista mediante el registro de escritura anticipada (**WAL - Write-Ahead Logging**) y las propiedades **ACID** de PostgreSQL.

### 4. Tolerancia a Fallos y Alta Disponibilidad Controlada

* **Concepto Teórico:** Capacidad de la red de mitigar cortes de comunicación (particiones) sin corromper el estado global del sistema.
* **Implementación Física:** Bajo la premisa del Teorema CAP, LibroNet prioriza la Consistencia sobre la Disponibilidad durante un fallo de red (**Enfoque CP**). Si una sede pierde conexión con el nodo central de la base de datos, los timeouts del backend abortan la transacción de forma segura. El frontend desarrollado en React captura la excepción de red en un bloque estructurado `try/catch` y degrada la interfaz elegantemente, desplegando alertas controladas al usuario en lugar de interrumpir la ejecución del navegador.

---

## 📂 Estructura Física del Proyecto

```text
biblioteca-distribuida/
├── backend/
│   ├── demo/                          # Servidor de Descubrimiento (Eureka Server)
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/com/example/demo/DemoApplication.java
│   │       └── resources/application.properties
│   │
│   ├── api-gateway/                   # Enrutador de la Red (Spring Cloud Gateway)
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/com/libronet/apigateway/ApiGatewayApplication.java
│   │       └── resources/application.yml
│   │
│   └── prestamos-service/             # Lógica Transaccional (Microservicio Core)
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/example/prestamos_service/
│           │   ├── PrestamosServiceApplication.java
│           │   ├── model/Libro.java
│           │   ├── repository/LibroRepository.java
│           │   ├── service/PrestamoService.java
│           │   └── controller/PrestamoController.java
│           └── resources/application.yml
│
└── frontend-libronet/                 # Interfaz de Usuario (React JS + Bootstrap)
    ├── package.json
    └── src/
        ├── main.jsx
        └── App.jsx
```

---

## 🚀 Guía de Despliegue Local (Sin Docker)

### Requisitos Previos

* Java Development Kit (JDK) 17 instalado.
* Apache Maven instalado y configurado en las variables de entorno.
* Node.js (versión 18 o superior) y npm.
* Motor de base de datos PostgreSQL activo en el puerto `5432`.

### Paso 1: Configuración de la Base de Datos

Accede a tu cliente de PostgreSQL (pgAdmin, psql, etc.) y ejecuta los siguientes comandos para crear la base de datos e insertar el libro inicial de prueba:

```sql
CREATE DATABASE biblioteca_db;

\c biblioteca_db;

CREATE TABLE libro (
    id UUID PRIMARY KEY,
    titulo VARCHAR(255),
    copias_disponibles INT
);

INSERT INTO libro (id, titulo, copias_disponibles) 
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'El Arte de la Escalabilidad', 1);
```

### Paso 2: Ejecución del Backend (Orden Estricto)

Abre tres terminales independientes en tu sistema operativo y arranca los componentes en la secuencia indicada:

1. **Terminal 1 - Servidor Eureka:**

```bash
cd backend/demo
mvn spring-boot:run
```

*Verificación:* Abre `http://localhost:8761` en tu navegador. Deberás visualizar el dashboard de Spring Eureka.

2. **Terminal 2 - API Gateway:**

```bash
cd backend/api-gateway
mvn spring-boot:run
```

*Verificación:* Actualiza el dashboard de Eureka; `LIBRONET-API-GATEWAY` debe figurar en la lista de instancias.

3. **Terminal 3 - Microservicio de Préstamos:**

```bash
cd backend/prestamos-service
mvn spring-boot:run
```

*Verificación:* `LIBRONET-PRESTAMOS` debe aparecer con estado **UP** en el dashboard de Eureka.

### Paso 3: Ejecución del Frontend

Abre una cuarta terminal para inicializar la interfaz de usuario:

```bash
cd frontend-libronet
npm run dev
```

*Verificación:* Abre `http://localhost:5173` en tu navegador para interactuar con el sistema web adaptado con Bootstrap.

---

## 🧪 Protocolo de Pruebas Físicas (Validación del Sistema)

### Prueba A: Verificación de Exclusión Mutua (Consistencia Fuerte)

1. Modifica la base de datos para que el libro de prueba tenga exactamente `1` copia disponible.
2. Abre dos ventanas de navegación diferentes (ej. una en modo normal y otra en modo incógnito) apuntando a `http://localhost:5173`.
3. Haz clic en el botón **"Solicitar Préstamo"** en ambas ventanas de forma simultánea.
4. **Resultado:** Una ventana desplegará un banner verde de **Éxito**, mientras que la otra obtendrá un banner rojo de **Denegado**. Inspecciona la consola del microservicio de préstamos; observarás la ejecución del comando SQL `SELECT ... FOR UPDATE`, demostrando que el motor encoló las peticiones y bloqueó el registro a nivel físico para evitar la sobreasignación.

### Prueba B: Validación de Tolerancia a Fallos

1. Con todo el sistema operativo, realiza un préstamo exitoso.
2. Dirígete a la Terminal 3 (`prestamos-service`) y detén el proceso abruptamente presionando `Ctrl + C`.
3. Regresa al navegador web e intenta realizar otra solicitud de préstamo.
4. **Resultado:** El sistema no colapsará. El API Gateway detectará la ausencia del nodo y devolverá un código de error controlado. La aplicación en React capturará la falla de red y mostrará una alerta limpia al usuario: *"Error de Conexión: El servicio central no responde o está saturado"*, demostrando un comportamiento elástico y una degradación elegante del servicio.