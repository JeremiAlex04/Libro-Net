# 🔗 REFERENCIAS RÁPIDAS - URLs Y COMANDOS

## 📱 URLs DEL FRONTEND

```
http://localhost:5173         # Frontend React
http://localhost:8761         # Eureka Service Registry  
http://localhost:8080         # API Gateway
http://localhost:5435         # PostgreSQL
```

---

## 🔴 TEMA 4: TOLERANCIA A FALLAS

### Endpoints Rápidos

```bash
# 1. Ver estado actual del nodo
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool

# 2. Ver eventos históricos
curl -s http://localhost:8080/api/eleccion/eventos | python -m json.tool

# 3. Ver SOLO: líder actual
curl -s http://localhost:8080/api/eleccion/estado | grep '"liderId"'

# 4. Ver SOLO: estado del nodo
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'

# 5. Ver SOLO: época de liderazgo
curl -s http://localhost:8080/api/eleccion/estado | grep '"liderazgoEpoca"'
```

### Simulación de Caída

```bash
# CAÍDA
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true" -s

# RESTAURACIÓN
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false" -s
```

### Lo que buscar durante la demo

**ANTES de caída:**
```
"liderId": 3              ← Hay líder
"estadoNode": "NORMAL"    ← Estado normal
"acceptingRequests": true ← Acepta operaciones
```

**DESPUÉS de caída (inmediato):**
```
"liderId": -1              ← Sin líder
"estadoNode": "OFFLINE"    ← Nodo caído
"acceptingRequests": false ← No acepta
```

**DESPUÉS de re-elección (10-15s):**
```
"liderId": 1 o 2           ← Nuevo líder elegido
"estadoNode": "NORMAL"     ← Operando normalmente
"liderazgoEpoca": 4        ← Época incrementó
```

---

## 🟡 TEMA 5: ATENUACIÓN

### Balanceo Round-Robin (5 veces seguidas)

```bash
for i in {1..5}; do echo "=== $i ===" && curl -s http://localhost:8080/api/catalogo/instancia; done
```

**Lo que esperar:** 
- Alternancia entre dos instancias distintas
- Ejemplo: `91a3e12bd99c:8082` → `f7ed6fa6fe53:8082` → `91a3e12bd99c:8082`

### Retry con Backoff (verificar en eventos)

```bash
curl -s http://localhost:8080/api/eleccion/eventos | python -m json.tool | tail -20
```

**Buscar patrones:**
- "Solicitar autorizacion"
- "ELECTION" messages con timestamp differences

### Ver timeout de backoff

```bash
# Progresión esperada:
# Intento 1 → 300ms
# Intento 2 → 600ms
# Intento 3 → 1200ms
# Intento 4-6 → 2000ms
```

---

## 🟢 TEMA 6: COMUNICACIÓN

### URLs Web

```
Eureka:    http://localhost:8761
Frontend:  http://localhost:5173
Gateway:   http://localhost:8080
```

### Ver servicios registrados

```bash
curl -s http://localhost:8761/eureka/apps | python -m json.tool | head -50
```

### Ver anillo lógico

```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -A 10 '"anillo"'
```

**Lo que esperar:**
```json
"anillo": [
  {"name": "Sede Norte", "id": 1, "uri": "http://172.18.0.4:8081"},
  {"name": "Sede Sur", "id": 2, "uri": "http://172.18.0.5:8083"},
  {"name": "Sede Este", "id": 3, "uri": "http://172.18.0.6:8084"}
]
```

### Ver headers de trazabilidad

```bash
curl -i http://localhost:8080/api/catalogo/buscar?query=libro 2>&1 | grep -i "X-Gateway\|X-LibroNet"
```

**Lo que esperar:**
```
X-Gateway-Routed-Host: 172.18.0.8:8082
X-LibroNet-Instance: libronet-catalogo
```

### Endpoint de Servidor de Tiempo (Cristian)

```bash
curl -s http://localhost:8080/api/time
```

**Respuesta esperada:**
```json
{"serverTimeMs": 1689255323456}
```

---

## 🔵 TEMA 7: RECUPERACIÓN DE FALLAS

### Estados de RESYNC

```bash
# NORMAL
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode": "NORMAL"'

# SIMULAR CAÍDA → OFFLINE
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true" -s

# CHECK OFFLINE (inmediato)
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode": "OFFLINE"'

# RESTAURAR → RESYNC
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false" -s

# CHECK RESYNC (2-8 segundos)
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode": "RESYNC"'

# ESPERAR A NORMAL
sleep 10

# CHECK NORMAL
curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode": "NORMAL"'
```

### Ver toda la información de RESYNC

```bash
curl -s http://localhost:8080/api/eleccion/estado | python -m json.tool | grep -E '"estadoNode"|"acceptingRequests"|"isOffline"|"lastClockSyncMs"|"liderazgoEpoca"'
```

### Lo que buscar en cada fase

**Fase 1: Sincronización de Reloj**
- `"lastClockSyncMs"` se actualiza
- Contacto con servidor de tiempo en API Gateway

**Fase 2: Consulta de Anillo**
- `"liderazgoEpoca"` se incrementa
- Se obtiene estado de todos los nodos activos

**Fase 3: Habilitación**
- `"estadoNode"` cambia de RESYNC a NORMAL
- `"acceptingRequests"` se vuelve `true`

---

## 🚀 ONE-LINERS PARA DEMO RÁPIDA

```bash
# Mostrar líder actual
curl -s http://localhost:8080/api/eleccion/estado | grep -o '"liderId":[0-9-]*'

# Mostrar estado del nodo
curl -s http://localhost:8080/api/eleccion/estado | grep -o '"estadoNode":"[^"]*"'

# Mostrar aceptación de peticiones
curl -s http://localhost:8080/api/eleccion/estado | grep -o '"acceptingRequests":[^,]*'

# Mostrar época
curl -s http://localhost:8080/api/eleccion/estado | grep -o '"liderazgoEpoca":[0-9]*'

# Simular caída (one-liner)
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=true" && sleep 2 && curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'

# Restaurar (one-liner)
curl -X POST "http://localhost:8080/api/eleccion/simular-caida?offline=false" && sleep 5 && curl -s http://localhost:8080/api/eleccion/estado | grep '"estadoNode"'
```

---

## 📊 TABLA DE ESTADOS

| Estado | isOffline | estadoNode | acceptingRequests | Descripción |
|--------|-----------|-----------|------------------|-------------|
| 🟢 Operacional | false | NORMAL | true | Nodo funciona normalmente |
| 🟡 Eligiendo | false | ELECTION | false | En proceso de elección |
| 🟠 Recuperando | false | RESYNC | false | Sincronizando post-caída |
| 🔴 Caído | true | OFFLINE | false | Nodo offline/simula caída |

---

## 💡 TIPS PARA LA DEMOSTRACIÓN

1. **Tener dos terminales abiertas:**
   - Terminal 1: Ejecutar comandos
   - Terminal 2: Verificar cambios en tiempo real

2. **Grabar la demostración:**
   ```bash
   # Linux/Mac
   asciinema rec demo.cast
   
   # Windows (usar herramienta de grabación integrada)
   ```

3. **Mostrar logs en tiempo real:**
   ```bash
   docker-compose logs -f prestamos-norte
   docker-compose logs -f prestamos-sur
   docker-compose logs -f prestamos-este
   ```

4. **Hacer preguntas interactivas:**
   - "¿Cuánto tiempo tarda la detección de falla?" (5 segundos ≈ heartbeat)
   - "¿Cuánto tarda la re-elección?" (2-5 segundos)
   - "¿Qué pasa si ambos nodos caen?" (el sistema entra en estado de ELECTION indefinido)

5. **Mostrar código relevante:**
   - Abrir `LiderEleccionService.java` 
   - Mostrar `@Scheduled(fixedRate = 5000)` para heartbeat
   - Mostrar método `resincronizarNodo()` para RESYNC

---

## ⚠️ POSIBLES PROBLEMAS Y SOLUCIONES

| Problema | Solución |
|----------|----------|
| "Connection refused" en localhost:8080 | Esperar a que Docker levante todos los servicios (20-30s) |
| Error 503 al intentar operación | Nodo probablemente está en RESYNC o OFFLINE; esperar o verificar estado |
| Época no incrementa | Puede ser que no haya habido re-elección; simular caída para verlo |
| Eventos vacío | El historial se limpia; hizo caida/restauración hace poco |
| `python -m json.tool` no funciona | Usar `jq` o instalar python3: `pip install python3` |

---

## 📝 NOTAS PARA APUNTES

**Escribir en la pizarra durante la demo:**

```
TOLERANCIA A FALLAS:
- Heartbeat: 5 segundos ✓
- Re-elección: Automática ✓
- Nuevo líder: En 2-8 segundos ✓

ATENUACIÓN:
- Balanceo: Round-robin ✓
- Retry: 300 → 600 → 1200 → 2000ms ✓
- Rollback: Transaccional ✓

COMUNICACIÓN:
- Descubrimiento: Eureka ✓
- Trazabilidad: Headers ✓
- P2P: Anillo lógico ✓

RECUPERACIÓN:
- Fase 1: Reloj (Cristian) ✓
- Fase 2: Anillo (Eureka) ✓
- Fase 3: Habilitación ✓
```

---

**Última actualización**: 13/07/2026  
**Estado**: ✅ Listo para usar en sustentación
