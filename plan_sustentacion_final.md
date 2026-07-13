# Plan de Sustentacion Final - LibroNet (Grupo 3)

Documento de cierre para convertir las recomendaciones del docente en una sustentacion demostrativa, trazable y defendible por todo el grupo.

---

## 1. Objetivo del plan

Este plan tiene 3 objetivos practicos:

1. Estandarizar las pruebas con evidencia verificable.
2. Alinear cada concepto de Sistemas Distribuidos con su implementacion real en LibroNet.
3. Ordenar una demo reproducible con participacion equilibrada de todo el equipo.

---

## 2. Checklist de recomendaciones del docente

| Recomendacion | Estado | Accion concreta | Evidencia a mostrar |
|---|---|---|---|
| 3.1 Fortalecer seccion de pruebas | En progreso | Usar tabla formal del punto 3 de este documento | Tabla + capturas/logs |
| 3.2 Ampliar conceptos SD | En progreso | Usar matriz concepto-componente del punto 4 | Tabla + endpoints + clases |
| 3.3 Diferenciar Dekker vs control real | En progreso | Exponer comparativa del punto 5 | Diagrama + codigo + query lock |
| 3.4 Mayor evidencia de codigo | En progreso | Usar lista de archivos clave del punto 6 | Capturas de archivos y logs |
| 3.5 Trazabilidad por integrante | En progreso | Completar matriz del punto 7 | Tabla de aportes + commits |
| 3.6 Demo ordenada | En progreso | Ejecutar guion del punto 8 | Demo en vivo + evidencia |
| 3.7 Conclusiones tecnicas | En progreso | Usar estructura del punto 9 | Conclusiones por mecanismo |

---

## 3. Tabla formal de pruebas (para informe final)

Completar esta tabla con resultados reales de ejecucion.

| Prueba | Escenario | Resultado esperado | Resultado obtenido | Evidencia |
|---|---|---|---|---|
| Prestamo local concurrente | Dos solicitudes sobre el ultimo ejemplar fisico | Solo una operacion descuenta stock; la otra falla o espera y luego falla por stock | Pendiente de completar | Log SQL con lock + estado final en BD + respuesta API |
| Balanceo round-robin catalogo | 10+ peticiones seguidas a /api/catalogo/instancia por gateway | Alternancia entre instancias de catalogo | Pendiente de completar | Header X-Gateway-Routed-Host + X-LibroNet-Instance + logs [ROUTING] |
| Cristian (sincronizacion temporal) | Nodo con drift consulta /api/time via gateway al registrar prestamo | Se calcula RTT y fecha_solicitud corregida | Pendiente de completar | JSON/log de sincronizacion + registro en tabla prestamo |
| Caida de lider | Lider cae durante operacion inter-sede o se fuerza caida | Se dispara ELECTION/COORDINATOR y emerge nuevo lider | Pendiente de completar | /api/eleccion/eventos + /api/eleccion/estado por sede |
| Recuperacion de nodo | Nodo restaurado desde OFFLINE | Entra RESYNC, no atiende negocio hasta converger y luego queda NORMAL | Pendiente de completar | /api/eleccion/estado (acceptingRequests, estadoNode, lastClockSyncMs) |

### 3.1 Procedimiento rapido para recolectar evidencia

1. Levantar stack completo con Docker Compose.
2. Ejecutar cada prueba con timestamp visible.
3. Guardar para cada prueba:
- captura del frontend,
- salida de endpoint,
- log del contenedor involucrado,
- evidencia final en BD si aplica.
4. Nombrar evidencias por prefijo:
- P1_local_concurrencia_*.png
- P2_round_robin_*.png
- P3_cristian_*.png
- P4_caida_lider_*.png
- P5_resync_*.png

---

## 4. Matriz de conceptos de Sistemas Distribuidos (donde esta cada concepto)

| Concepto SD | Donde aparece en LibroNet | Componente responsable | Evidencia tecnica |
|---|---|---|---|
| Arquitectura distribuida | Servicios desacoplados por red | Frontend + Gateway + Catalogo + Prestamos + Eureka + PostgreSQL | docker-compose.yml |
| Gateway / enrutamiento | Punto unico de entrada y ruteo por path | api-gateway | Rutas en application.yml |
| Descubrimiento dinamico | Registro y resolucion de instancias | Eureka + clientes Eureka | eureka client config + panel Eureka |
| Balanceo de carga | Round-robin entre instancias | Spring Cloud LoadBalancer en gateway | GatewayRoundRobinLoadBalancerConfig + headers de instancia |
| Comunicacion HTTP/REST | Llamadas entre servicios y cliente | Controladores REST | Endpoints /api/* |
| Exclusión mutua academica | Simulacion de acceso critico concurrente | DekkerSimulationController | /api/simulacion/dekker |
| Exclusión mutua real | Control transaccional de inventario | PrestamoService + LibroRepository | @Lock(PESSIMISTIC_WRITE) + SELECT FOR UPDATE |
| Sincronizacion temporal | Ajuste por RTT/2 sobre tiempo de referencia | PrestamoService + TimeController | /api/time + campos drift/rtt en prestamo |
| Eleccion de lider | Chang-Roberts en anillo logico | LiderEleccionService | eventos ELECTION/COORDINATOR |
| Tolerancia a fallos | Deteccion de caida y reelection | LiderEleccionService + heartbeat | /api/eleccion/estado + /eventos |
| Recuperacion controlada | Reingreso con RESYNC previo a operar | LiderEleccionService | acceptingRequests + lastClockSyncMs |
| Observabilidad | Trazabilidad de ruteo e instancia | GatewayRouteAuditFilter + frontend auditoria | headers + panel de auditoria |

---

## 5. Aclaracion clave para sustentacion: Dekker vs PESSIMISTIC_WRITE

### 5.1 Mensaje corto para decir en exposicion

"En LibroNet usamos Dekker V5 como simulacion academica para demostrar el principio de exclusion mutua. El control real de inventario en produccion se garantiza con bloqueo pesimista transaccional en PostgreSQL mediante PESSIMISTIC_WRITE."

### 5.2 Diferencia tecnica

| Mecanismo | Rol en el proyecto | Alcance | Limitacion |
|---|---|---|---|
| Dekker V5 | Evidencia didactica de exclusion mutua | Simulacion in-memory en hilos | No coordina nodos distribuidos reales |
| PESSIMISTIC_WRITE | Control real del stock | Transaccion BD multi-instancia | Depende de disponibilidad de la BD |

---

## 6. Evidencia de codigo que SI deben mostrar

Tomar capturas de estos archivos/clases durante la presentacion:

1. Configuracion round-robin:
- api-gateway/src/main/java/com/example/api_gateway/GatewayRoundRobinLoadBalancerConfig.java

2. Auditoria de ruteo en gateway:
- api-gateway/src/main/java/com/example/api_gateway/GatewayRouteAuditFilter.java

3. Endpoint de tiempo (Cristian):
- api-gateway/src/main/java/com/example/api_gateway/TimeController.java

4. Lock pesimista (control real):
- prestamos-service/src/main/java/com/example/prestamos_service/repository/LibroRepository.java
- prestamos-service/src/main/java/com/example/prestamos_service/service/PrestamoService.java

5. Eleccion de lider y recuperacion:
- prestamos-service/src/main/java/com/example/prestamos_service/service/LiderEleccionService.java
- prestamos-service/src/main/java/com/example/prestamos_service/controller/EleccionController.java

6. Configuracion de despliegue distribuido:
- docker-compose.yml

---

## 7. Trazabilidad por integrante (completar antes de sustentar)

| Integrante | Responsabilidad principal | Modulo / entregable | Evidencia (commit, archivo, captura, demo) |
|---|---|---|---|
| Paredes Merino, Zahid | Completar | Completar | Completar |
| Rosales Alvarez, Kevin | Completar | Completar | Completar |
| Olivares Chavez, Jeremi | Completar | Completar | Completar |
| Cortez Pacheco, Angelo Jesus | Completar | Completar | Completar |
| Martinez Esparza, Samuel Fabrizio | Completar | Completar | Completar |

### 7.1 Regla para defender individualmente

Cada integrante debe dominar:

1. Un componente principal (backend, gateway, frontend auditoria, BD, despliegue).
2. Un concepto SD asociado a ese componente.
3. Una evidencia concreta que pueda mostrar en menos de 60 segundos.

---

## 8. Guion de demo ordenada (recomendado por el docente)

Duracion sugerida: 12 a 15 minutos.

### 8.1 Orden de demostracion

1. Levantar stack:
- docker compose up -d --build

2. Mostrar Eureka:
- Ver servicios registrados (catalogo x2, prestamos x3, gateway).

3. Ingreso al sistema:
- Login con sede valida.

4. Consulta de catalogo:
- Buscar libro por titulo.

5. Evidenciar round-robin:
- Repetir consultas y mostrar alternancia por headers/logs.

6. Prestamo local:
- Ejecutar prestamo con stock local y mostrar estado ENTREGADO.

7. Prestamo inter-sede:
- Ejecutar prestamo sin stock local y mostrar PENDIENTE_DE_ENVIO.

8. Simular caida de lider:
- Cambiar nodo lider a OFFLINE y mostrar eventos de eleccion.

9. Mostrar nuevo lider:
- Verificar convergencia en /api/eleccion/estado.

10. Recuperar nodo caido:
- Restaurar nodo y evidenciar paso por RESYNC antes de NORMAL.

11. Cierre:
- Resumen de limitaciones y mejoras futuras.

### 8.2 Plan de contingencia en vivo

Si falla la demo en vivo:

1. Mostrar evidencia pregrabada (capturas + logs con timestamp).
2. Ejecutar al menos una prueba corta en vivo (ejemplo: round-robin).
3. Mantener narrativa tecnica: problema -> mecanismo -> evidencia -> conclusion.

---

## 9. Estructura de conclusiones tecnicas (para informe y cierre oral)

Usar 4 conclusiones, una por bloque tecnico:

1. Concurrencia:
- Que problema resolvio (sobreventa del ultimo ejemplar).
- Mecanismo real aplicado (PESSIMISTIC_WRITE).
- Resultado observado.

2. Tiempo distribuido:
- Que problema resolvio (desalineacion de reloj entre nodos).
- Mecanismo aplicado (Cristian con RTT/2).
- Resultado observado (drift/rtt trazables).

3. Tolerancia a fallos:
- Que problema resolvio (caida del lider en operaciones inter-sede).
- Mecanismo aplicado (Chang-Roberts + retry/backoff + RESYNC).
- Resultado observado (reeleccion y recuperacion).

4. Limitaciones y mejora realista:
- Dependencia de BD central.
- Ventana temporal sin lider para inter-sede.
- Mejora futura: consenso tipo Raft/Paxos y persistencia distribuida del estado de liderazgo.

---

## 10. Banco de preguntas para preparar al grupo (con respuestas breves)

1. Por que LibroNet es distribuido?
- Porque esta compuesto por procesos independientes en nodos/servicios separados que coordinan por red para ofrecer una unica funcionalidad.

2. Funcion del API Gateway?
- Entrada unica, ruteo por path, balanceo hacia servicios, CORS y trazabilidad de peticiones.

3. Papel de Eureka?
- Registro y descubrimiento dinamico de instancias para evitar dependencias estaticas.

4. Como prueban round-robin?
- Con peticiones repetidas a endpoints de instancia y verificacion por headers X-Gateway-Routed-Host y X-LibroNet-Instance.

5. Que resuelve Cristian?
- Reduce inconsistencias temporales entre nodos al corregir hora local usando tiempo de referencia y RTT.

6. Diferencia Dekker vs PESSIMISTIC_WRITE?
- Dekker: simulacion academica de exclusion mutua; PESSIMISTIC_WRITE: mecanismo real de consistencia en BD.

7. Que pasa con dos bibliotecarios por ultimo ejemplar?
- El lock pesimista serializa el acceso: uno descuenta stock y el otro no puede confirmar la misma unidad.

8. Como funciona Chang-Roberts?
- Se propagan mensajes ELECTION en anillo con IDs; el mayor ID activo se declara lider y difunde COORDINATOR.

9. Que pasa si cae lider en operacion inter-sede?
- Se reintenta autorizacion con backoff, se invalida lider, se fuerza eleccion y se continua o se hace rollback limpio.

10. Que significa RESYNC?
- Estado de recuperacion donde el nodo restaurado no atiende negocio hasta resincronizar reloj y converger con lider/epoca.

11. Limitaciones frente a Raft/Paxos?
- Mayor ventana sin coordinador y menor robustez de consenso fuerte; se priorizo simplicidad academica.

12. Que hizo cada integrante?
- Debe responderse con la tabla del punto 7, basada en evidencia verificable.

---

## 11. Entregables minimos antes de la sustentacion

1. Tabla de pruebas completada con resultados reales.
2. Carpeta de evidencias etiquetada por prueba.
3. Trazabilidad por integrante completa.
4. Guion de demo practicado al menos 2 veces.
5. Banco de preguntas ensayado por todos los integrantes.

---

## 12. Ejecucion automatizada de pruebas y evidencias

Se agrego un script para correr las pruebas clave y guardar evidencia en una carpeta con timestamp.

Archivo:
- scripts/ejecutar_pruebas_sustentacion.ps1

Comando de ejecucion (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ejecutar_pruebas_sustentacion.ps1
```

Salida esperada:
- Carpeta: `evidencias/pruebas_YYYYMMDD_HHMMSS/`
- Archivos JSON/TXT por prueba:
	- P1_concurrencia_respuestas.json
	- P1_stock_final.txt
	- P2_round_robin.json
	- P3_cristian_prestamo_response.json
	- P3_cristian_prestamo_registro.json
	- P4_estados_antes_caida.json
	- P4_estados_despues_caida.json
	- P4_eventos_lider_caido.json
	- P5_estado_resync_t1.json
	- P5_estado_resync_t2.json
	- logs_servicios_tail200.txt

Uso directo para la tabla del informe:
1. Copiar resultados de cada archivo a la columna "Resultado obtenido".
2. Colocar el nombre de archivo en la columna "Evidencia".
3. Adjuntar capturas del frontend/Eureka para complementar la evidencia tecnica.
