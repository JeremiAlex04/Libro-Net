# 📋 HOJA DE TRUCOS RÁPIDA - LIBRONET SUSTENTACIÓN

## 🌐 ABRIR PRIMERO

```
Frontend:  http://localhost:5173
Eureka:    http://localhost:8761
Gateway:   http://localhost:8080
```

---

## 🔴 TEMA 4 - TOLERANCIA A FALLAS (5 min)

### Ver estado actual
```bash
curl -s http://localhost:8080/api/eleccion/estado | grep -o '"liderId":[0-9-]*'
curl -s http://localhost:8080/api/eleccion/estado | grep -o '"estadoNode":"[^"]*"'
```

### Simular caída → OFFLINE
```bash
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true"
```
Verificar: `liderId=-1`, `estadoNode=OFFLINE`, `acceptingRequests=false`

### Esperar 10-15 segundos → nueva elección

### Ver nuevo líder elegido
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool
```
Esperar: `liderId=1 o 2`, `estadoNode=NORMAL`, `liderazgoEpoca=4`

**Concepto**: El heartbeat cada 5s detecta falla → re-elección automática

---

## 🟡 TEMA 5 - ATENUACIÓN (3 min)

### Balanceo round-robin
```bash
for i in {1..5}; do echo "$i:" && curl -s http://localhost:8080/api/catalogo/instancia | python -m json.tool; done
```
Esperar: alternancia entre `91a3e12bd99c:8082` ↔ `f7ed6fa6fe53:8082`

### Ver backoff en eventos
```bash
curl -s http://localhost:8080/api/eleccion/eventos | python -m json.tool | tail -30
```
Buscar: timestamps de ELECTION messages con diferencias: 300ms → 600ms → 1200ms → 2000ms

**Conceptos**: 
- Balanceo distribuye carga
- Retry exponencial evita saturar red
- Rollback automático por @Transactional

---

## 🟢 TEMA 6 - COMUNICACIÓN (4 min)

### Eureka - Servicios registrados
```
Abrir: http://localhost:8761
Buscar tabla: LIBRONET-CATALOGO (2), LIBRONET-PRESTAMOS (3)
```

### Frontend
```
Abrir: http://localhost:5173
Mostrar: Panel de control de sedes
```

### Anillo lógico
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -A 10 '"anillo"'
```
Esperar: 1→2→3 en orden

### Headers de trazabilidad
```bash
curl -i http://localhost:8080/api/catalogo/buscar?query=libro 2>&1 | grep -i "X-Gateway\|X-LibroNet"
```

**Concepto**: Descubrimiento dinámico, P2P para elección, auditoría

---

## 🔵 TEMA 7 - RECUPERACIÓN DE FALLAS (6 min)

### 1. Ver estado NORMAL
```bash
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'
```
Resultado: `"estadoNode":"NORMAL"`

### 2. Simular caída → OFFLINE
```bash
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true"
```
Resultado inmediato: `"estadoNode":"OFFLINE"`, `acceptingRequests=false`

### 3. Restaurar → RESYNC
```bash
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false"
```

### 4. Ver RESYNC (2-8 segundos después)
```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"estadoNode"|"acceptingRequests"|"lastClockSyncMs"'
```
Resultado: `"estadoNode":"RESYNC"`, `acceptingRequests=false`, `lastClockSyncMs=UPDATE`

### 5. Esperar convergencia (10 segundos)

### 6. Ver NORMAL
```bash
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'
```
Resultado final: `"estadoNode":"NORMAL"`, `acceptingRequests=true`

**Las 3 Fases**:
- **Fase 1**: Sincronización de reloj (Cristian) ✓
- **Fase 2**: Consulta del anillo activo (Eureka) ✓
- **Fase 3**: Habilitación de negocio ✓

---

## 🚨 ESTADOS DE MÁQUINA

```
NORMAL (🟢)
  ↓ (detecta falta de líder)
ELECTION (🟡)
  ↓ (nuevo líder elegido)
NORMAL (🟢)

---

Desde caída:
OFFLINE (🔴) → simulación
  ↓ (offline=false)
RESYNC (🟠) → 3 fases
  ↓ (convergencia)
NORMAL (🟢)
```

---

## 🎯 PUNTOS CRÍTICOS

| Tema | Punto Clave |
|------|------------|
| **4** | Heartbeat 5s detecta falla automáticamente |
| **5** | Backoff exponencial: 300→600→1200→2000ms |
| **6** | Eureka descubre servicios dinámicamente |
| **7** | RESYNC protege contra inconsistencias |

---

## ⚠️ SI ALGO FALLA

```bash
# Servicios no responden
docker-compose ps

# Reiniciar todo
docker-compose down
docker-compose up -d

# Ver logs en tiempo real
docker-compose logs -f prestamos-norte
docker-compose logs -f prestamos-sur
docker-compose logs -f prestamos-este

# HTTP 503
→ Nodo en RESYNC, esperar 10 segundos
```

---

## ✅ ANTES DE PRESENTAR

- [ ] Docker corriendo: `docker-compose ps`
- [ ] Gateway responde: http://localhost:8080/api/eleccion/estado
- [ ] Eureka activo: http://localhost:8761
- [ ] Frontend carga: http://localhost:5173
- [ ] Practicó los comandos
- [ ] Entiende los 4 estados
- [ ] Puede explicar las 3 fases RESYNC

---

**Versión**: 1.0 | **Imprime esto y lleva a la sustentación**
