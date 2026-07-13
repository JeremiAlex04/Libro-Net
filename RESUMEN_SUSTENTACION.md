# 🎯 RESUMEN EJECUTIVO - SUSTENTACIÓN VISUAL LIBRONET

## Información General
- **Proyecto**: LibroNet - Sistema Distribuido de Gestión de Préstamos Bibliotecarios
- **Temas Asignados**: 4, 5, 6, 7
- **Fecha de Sustentación**: 13/07/2026
- **Estado del Proyecto**: ✅ COMPLETAMENTE FUNCIONAL

---

## 📂 ARCHIVOS DE PREPARACIÓN CREADOS

Los siguientes archivos se encuentran en el repositorio raíz para la sustentación:

1. **`CAPTURAS_VISUALES_SUSTENTACION.md`**
   - Detalles de todas las capturas tomadas
   - Explicación visual de cada tema
   - Interpretación de datos JSON

2. **`DEMO_SUSTENTACION.md`**
   - Guía paso a paso de demostración en vivo
   - Comandos para ejecutar
   - Resultados esperados para cada paso

3. **`REFERENCIAS_RAPIDAS.md`**
   - URLs y endpoints directos
   - One-liners para demostración rápida
   - Tabla de estados y solución de problemas

---

## 🎬 PANTALLAS CAPTURADAS

### Tema 4: Tolerancia a Fallas
- ✅ Estado de elección actual (Sede Sur en ELECTION)
- ✅ Anillo lógico de 3 nodos
- ✅ Eventos históricos de detección y re-elección
- ✅ Proof of automatic heartbeat and election

### Tema 5: Atenuación
- ✅ Balanceo round-robin entre 2 réplicas de catálogo
- ✅ Historial de reintentos con backoff exponencial
- ✅ Rollback automático en transacciones fallidas

### Tema 6: Comunicación
- ✅ Eureka Service Registry con todos los servicios
- ✅ Frontend React mostrando interfaz HTTP/REST
- ✅ Headers de trazabilidad (X-Gateway-Routed-Host, X-LibroNet-Instance)
- ✅ Anillo lógico de P2P para elección

### Tema 7: Recuperación de Fallas
- ✅ Estado OFFLINE (nodo caído)
- ✅ Estado RESYNC (en proceso de recuperación)
- ✅ Estado NORMAL (post-recuperación exitosa)
- ✅ Transiciones de máquina de estados

---

## 🔗 ENDPOINTS CRÍTICOS POR TEMA

### Tema 4: Tolerancia a Fallas
```
GET /api/eleccion/estado
GET /api/eleccion/eventos
POST /api/eleccion/simular-caida?offline=true
POST /api/eleccion/simular-caida?offline=false
```

### Tema 5: Atenuación
```
GET /api/catalogo/instancia          (Balanceo round-robin)
GET /api/eleccion/eventos            (Reintentos con backoff)
POST /api/prestamos/{id}             (Rollback transaccional)
```

### Tema 6: Comunicación
```
http://localhost:8761                (Eureka registry)
http://localhost:5173                (Frontend)
GET /api/eleccion/estado             (Anillo lógico)
GET /api/time                        (Servidor de tiempo)
```

### Tema 7: Recuperación de Fallas
```
GET /api/eleccion/estado             (Ver estado actual)
POST /api/eleccion/simular-caida     (Simular/restaurar)
POST /api/eleccion/resincronizar    (Forzar RESYNC)
```

---

## 🎓 CONCEPTOS CLAVE A EXPLICAR

### Tema 4: Introducción a Tolerancia a Fallas

**Concepto Principal**: 
Sistema que continúa operando cuando fallan componentes sin intervención manual.

**Mecanismos en LibroNet**:
1. **Heartbeat periódico** (5 segundos) - Detecta si el líder responde
2. **Re-elección automática** - Cuando falla el líder
3. **Términos/Épocas** - Previenen split-brain

**Evidencia en pantalla**:
```
ANTES: liderId=3, estadoNode="NORMAL"
CAÍDA: POST /simular-caida?offline=true
EFECTO: liderId=-1, estadoNode="OFFLINE", acceptingRequests=false
RESUELTO: liderId=2, estadoNode="NORMAL", liderazgoEpoca=4
```

**Tiempo de convergencia**: ~5-8 segundos (1 heartbeat + elección)

---

### Tema 5: Atenuación

**Concepto Principal**: 
Reducir el impacto de fallas mediante técnicas que mantienen operación degradada.

**Mecanismos en LibroNet**:
1. **Balanceo round-robin** - Distribuye carga entre réplicas
2. **Retry con backoff exponencial** - 300ms → 600ms → 1200ms → 2000ms
3. **Rollback transaccional** - Revierte si no se logra autorización
4. **Degradación selectiva** - Ops locales funcionan sin líder

**Evidencia en pantalla**:
```
BALANCEO: /api/catalogo/instancia alternancia entre 2 IDs
RETRY: Eventos muestran ELECTION(2), ELECTION(3), ELECTION(2)...
TIEMPO: Timestamps en eventos demuestran backoff exponencial
```

**Ventaja**: Minimiza impacto de fallas transitorias sin sacrificar consistencia

---

### Tema 6: Comunicación

**Concepto Principal**: 
Múltiples capas de comunicación distribuida con descubrimiento dinámico.

**Capas de Comunicación**:
1. **Capa 1**: Frontend → API Gateway → Servicios (HTTP/REST)
2. **Capa 2**: Servicio → Servicio vía Eureka (nombres lógicos)
3. **Capa 3**: Nodo → Nodo P2P (anillo lógico, mensajes ELECTION/COORDINATOR)

**Evidencia en pantalla**:
```
EUREKA: 3 servicios registrados, 5+ instancias activas
FRONTEND: Interfaz conecta a localhost:8080
ANILLO: Norte(1) → Sur(2) → Este(3) → Norte(1)
HEADERS: X-Gateway-Routed-Host, X-LibroNet-Instance
```

**Ventaja**: Sin dependencias estáticas, escalable, auditable

---

### Tema 7: Recuperación de Fallas (RESYNC)

**Concepto Principal**: 
Protocolo estructurado para que nodos recuperados se reincorporen consistentemente.

**3 Fases de RESYNC**:

**Fase 1: Sincronización de Reloj**
- Contacta servidor de tiempo (API Gateway)
- Aplica Algoritmo de Cristian: T_corregido = T_server + RTT/2
- Campo: `lastClockSyncMs` se actualiza

**Fase 2: Consulta de Anillo Activo**
- Consulta Eureka para encontrar nodos
- Obtiene estado de TODOS los nodos
- Adopta líder vigente o inicia nueva elección
- Campo: `liderazgoEpoca` se sincroniza

**Fase 3: Habilitación Gradual**
- Cambio: `estadoNode: RESYNC → NORMAL`
- Cambio: `acceptingRequests: false → true`
- Ahora acepta operaciones de negocio

**Evidencia en pantalla**:
```
OFFLINE: isOffline=true, estadoNode="OFFLINE", acceptingRequests=false
RESYNC: isOffline=false, estadoNode="RESYNC", acceptingRequests=false, lastClockSyncMs=UPDATE
NORMAL: isOffline=false, estadoNode="NORMAL", acceptingRequests=true
```

**Duración típica**: 2-8 segundos (dependiendo de convergencia de elección)

---

## 📊 MATRIZ DE DEMOSTRACIÓN

| Tema | Qué Demostrar | Comando/URL | Resultado Esperado |
|------|---|---|---|
| **4** | Détection de falla | POST /simular-caida?offline=true | liderId=-1, ELECTION state |
| **4** | Re-elección | Esperar 10s | Nuevo líder elegido, época incrementa |
| **5** | Balanceo | GET /catalogo/instancia x5 | IDs alternados |
| **5** | Backoff | GET /eleccion/eventos | Timestamps con diferencias exponenciales |
| **6** | Eureka | http://localhost:8761 | 3 servicios, 5+ instancias |
| **6** | Frontend | http://localhost:5173 | Panel de login cargado |
| **6** | Anillo | GET /eleccion/estado | 3 nodos en orden: 1→2→3 |
| **7** | RESYNC | POST /simular-caida?offline=false | Estado transita OFFLINE→RESYNC→NORMAL |
| **7** | Protección | Intentar op en RESYNC | HTTP 503: "En resincronización" |

---

## 🚀 SECUENCIA DE DEMOSTRACIÓN RECOMENDADA (25 minutos)

### 1️⃣ INTRODUCCIÓN (2 minutos)
- Explicar arquitectura general
- Mostrar Eureka: 3 servicios, anillo lógico
- Mostrar Frontend

### 2️⃣ TOLERANCIA A FALLAS (5 minutos)
- Ver estado actual (hay líder, NORMAL)
- Simular caída
- Mostrar estado inmediato (OFFLINE)
- Esperar 10-15 segundos
- Mostrar nuevo líder elegido, época incrementada

### 3️⃣ ATENUACIÓN (3 minutos)
- Ejecutar `/catalogo/instancia` 5 veces
- Mostrar alternancia de instancias
- Explicar balanceo round-robin
- Mostrar en eventos el backoff exponencial

### 4️⃣ COMUNICACIÓN (5 minutos)
- Abrir Eureka (localhost:8761)
- Señalar servicios registrados dinámicamente
- Abrir Frontend (localhost:5173)
- Explicar capas: Frontend → Gateway → Servicios
- Mostrar anillo lógico en `/estado`
- Mostrar headers de trazabilidad en DevTools

### 5️⃣ RECUPERACIÓN DE FALLAS (8 minutos)
- Mostrar estado NORMAL
- Simular caída
- Mostrar estado OFFLINE (no acepta ops)
- Restaurar nodo
- Mostrar estado RESYNC (3 fases en progreso)
- Esperar 8-10 segundos
- Mostrar estado NORMAL post-RESYNC
- Explicar: Reloj sincronizado, Anillo consultado, Negocio habilitado

### 6️⃣ CONCLUSIONES (2 minutos)
- Resaltar automatización
- Ninguna intervención manual requerida
- Sistema convergió a estado consistente

---

## 💼 ESTRUCTURA DE PRESENTACIÓN SUGERIDA

**Diapositiva 1**: Portada
- Título: LibroNet - Sistema Distribuido
- Temas: 4, 5, 6, 7
- Fecha: 13/07/2026

**Diapositiva 2**: Arquitectura General
- Diagrama: Frontend → Gateway → Servicios
- Servicios: Catálogo (2), Prestamos (3)
- Descubrimiento: Eureka

**Diapositiva 3**: Tema 4 - Tolerancia a Fallas
- Concepto: Continúa operando ante fallas
- Mecanismo: Heartbeat (5s) + Re-elección
- Demostración en vivo

**Diapositiva 4**: Tema 5 - Atenuación
- Concepto: Reduce impacto de fallas
- Mecanismos: Round-robin, Retry, Rollback
- Demostración: Balanceo y backoff

**Diapositiva 5**: Tema 6 - Comunicación
- Concepto: Múltiples capas de comunicación
- Capas: HTTP/REST, Eureka, P2P
- Demostración: Eureka + Anillo

**Diapositiva 6**: Tema 7 - Recuperación
- Concepto: RESYNC en 3 fases
- Fases: Reloj, Anillo, Habilitación
- Demostración en vivo

**Diapositiva 7**: Conclusiones
- Automatización sin intervención manual
- Convergencia a estado consistente
- Arquitectura resiliente y auditada

---

## ✅ CHECKLIST PRE-SUSTENTACIÓN

- [ ] Docker está corriendo (docker-compose up -d)
- [ ] Todos los servicios están sanos (docker-compose ps)
- [ ] Eureka responde (http://localhost:8761)
- [ ] Frontend carga (http://localhost:5173)
- [ ] Gateway responde (http://localhost:8080/api/eleccion/estado)
- [ ] Los 3 archivos de documentación están listos:
  - [ ] CAPTURAS_VISUALES_SUSTENTACION.md
  - [ ] DEMO_SUSTENTACION.md
  - [ ] REFERENCIAS_RAPIDAS.md
- [ ] Se han practicado los comandos clave
- [ ] Se entiende cada estado: NORMAL, ELECTION, OFFLINE, RESYNC
- [ ] Se puede explicar las 3 fases de RESYNC
- [ ] Se puede demostrar balanceo round-robin
- [ ] Se entiende el anillo lógico: 1→2→3→1

---

## 🎬 COMANDOS DE DEMOSTRACIÓN EN VIVO

**Copiar y pegar en terminal según sea necesario:**

```bash
# INICIO RÁPIDO
docker-compose up -d
sleep 20

# TOLERANCIA A FALLAS
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"liderId"|"estadoNode"|"liderazgoEpoca"'
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true"
sleep 2
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"liderId"|"estadoNode"'

# ATENUACIÓN
for i in {1..5}; do curl -s http://localhost:8080/api/catalogo/instancia | python -m json.tool | grep '"instance"'; done

# COMUNICACIÓN
curl -s http://localhost:8761/eureka/apps | python -m json.tool 2>/dev/null | head -30

# RECUPERACIÓN
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false"
sleep 5
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"estadoNode"|"acceptingRequests"'
```

---

## 📚 REFERENCIAS EN DOCUMENTACIÓN DEL PROYECTO

- **Sincronización de Reloj**: Ver `Documentacion_Final.md` Sección 3
- **Exclusión Mutua**: Ver `Documentacion_Final.md` Sección 4
- **Elección**: Ver `Documentacion_Final.md` Sección 5
- **Tolerancia a Fallas**: Ver `Documentacion_Final.md` Sección 6
- **Atenuación**: Ver `Documentacion_Final.md` Sección 7
- **Comunicación**: Ver `Documentacion_Final.md` Sección 8
- **Recuperación**: Ver `Documentacion_Final.md` Sección 9

---

## 🎓 PUNTOS CRÍTICOS A RECORDAR

1. **Tolerancia a Fallas**:
   - "El heartbeat cada 5 segundos detecta automáticamente cuando el líder falla"
   - "La re-elección ocurre sin intervención manual del usuario"

2. **Atenuación**:
   - "El balanceo distribuye la carga entre múltiples instancias"
   - "Los reintentos usan backoff exponencial para no saturar la red"

3. **Comunicación**:
   - "Los servicios se descubren dinámicamente mediante Eureka"
   - "Cada petición queda traceable mediante headers"

4. **Recuperación**:
   - "El nodo recuperado NO acepta operaciones hasta completar RESYNC"
   - "Las 3 fases garantizan que el nodo esté sincronizado y consistente"

---

**Preparado por**: Sistema de Capacitación LibroNet  
**Fecha**: 13/07/2026  
**Versión**: 1.0  
**Estado**: ✅ LISTO PARA SUSTENTACIÓN
