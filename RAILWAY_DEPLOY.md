# Despliegue en Railway

Este proyecto no se despliega como un solo servicio. En Railway debes crear un proyecto y luego agregar varios servicios Docker a partir del mismo repositorio.

## Servicios a crear

Usa estos nombres de servicio en Railway para que las referencias de variables queden consistentes:

- `frontend`
- `gateway`
- `eureka`
- `catalogo`
- `prestamos_norte`
- `prestamos_sur`
- `prestamos_este`
- `postgres`

## Root Directory por servicio

- `frontend` -> `_frontend-libronet`
- `gateway` -> `api-gateway`
- `eureka` -> `red`
- `catalogo` -> `catalogo-service`
- `prestamos_norte` -> `prestamos-service`
- `prestamos_sur` -> `prestamos-service`
- `prestamos_este` -> `prestamos-service`

Cada carpeta ya incluye su `Dockerfile` y un `railway.json` con healthcheck y restart policy.

## Base de datos

Agrega PostgreSQL desde el canvas de Railway y renombra el servicio a `postgres`.

## Variables por servicio

### `eureka`

- `SPRING_PROFILES_ACTIVE=docker`

### `gateway`

- `SPRING_PROFILES_ACTIVE=docker`
- `SERVICE_HOST=${{RAILWAY_PRIVATE_DOMAIN}}`
- `EUREKA_BASE_URL=http://${{eureka.RAILWAY_PRIVATE_DOMAIN}}/eureka/`
- `PRESTAMOS_NORTE_URL=http://${{prestamos_norte.RAILWAY_PRIVATE_DOMAIN}}`
- `PRESTAMOS_SUR_URL=http://${{prestamos_sur.RAILWAY_PRIVATE_DOMAIN}}`
- `PRESTAMOS_ESTE_URL=http://${{prestamos_este.RAILWAY_PRIVATE_DOMAIN}}`
- `FRONTEND_ALLOWED_ORIGIN=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}`

### `catalogo`

- `SPRING_PROFILES_ACTIVE=docker`
- `SERVICE_HOST=${{RAILWAY_PRIVATE_DOMAIN}}`
- `EUREKA_BASE_URL=http://${{eureka.RAILWAY_PRIVATE_DOMAIN}}/eureka/`
- `DATABASE_URL=${{postgres.DATABASE_URL}}`
- `SPRING_DATASOURCE_USERNAME=${{postgres.PGUSER}}`
- `SPRING_DATASOURCE_PASSWORD=${{postgres.PGPASSWORD}}`

### `prestamos_norte`

- `SPRING_PROFILES_ACTIVE=docker`
- `SERVICE_HOST=${{RAILWAY_PRIVATE_DOMAIN}}`
- `EUREKA_BASE_URL=http://${{eureka.RAILWAY_PRIVATE_DOMAIN}}/eureka/`
- `GATEWAY_TIME_URL=http://${{gateway.RAILWAY_PRIVATE_DOMAIN}}/api/time`
- `DATABASE_URL=${{postgres.DATABASE_URL}}`
- `SPRING_DATASOURCE_USERNAME=${{postgres.PGUSER}}`
- `SPRING_DATASOURCE_PASSWORD=${{postgres.PGPASSWORD}}`

### `prestamos_sur`

- `SPRING_PROFILES_ACTIVE=docker,sede-sur`
- `SERVICE_HOST=${{RAILWAY_PRIVATE_DOMAIN}}`
- `EUREKA_BASE_URL=http://${{eureka.RAILWAY_PRIVATE_DOMAIN}}/eureka/`
- `GATEWAY_TIME_URL=http://${{gateway.RAILWAY_PRIVATE_DOMAIN}}/api/time`
- `DATABASE_URL=${{postgres.DATABASE_URL}}`
- `SPRING_DATASOURCE_USERNAME=${{postgres.PGUSER}}`
- `SPRING_DATASOURCE_PASSWORD=${{postgres.PGPASSWORD}}`

### `prestamos_este`

- `SPRING_PROFILES_ACTIVE=docker,sede-este`
- `SERVICE_HOST=${{RAILWAY_PRIVATE_DOMAIN}}`
- `EUREKA_BASE_URL=http://${{eureka.RAILWAY_PRIVATE_DOMAIN}}/eureka/`
- `GATEWAY_TIME_URL=http://${{gateway.RAILWAY_PRIVATE_DOMAIN}}/api/time`
- `DATABASE_URL=${{postgres.DATABASE_URL}}`
- `SPRING_DATASOURCE_USERNAME=${{postgres.PGUSER}}`
- `SPRING_DATASOURCE_PASSWORD=${{postgres.PGPASSWORD}}`

### `frontend`

- `GATEWAY_HOSTPORT=${{gateway.RAILWAY_PRIVATE_DOMAIN}}`

## Orden recomendado de despliegue

1. `postgres`
2. `eureka`
3. `catalogo`
4. `prestamos_norte`
5. `prestamos_sur`
6. `prestamos_este`
7. `gateway`
8. `frontend`

## Verificaciones mínimas

1. Abre el dominio público de `gateway` y prueba `/api/time`.
2. Abre el dominio público de `eureka` y verifica que estén registrados `libronet-catalogo`, `libronet-prestamos` y `libronet-api-gateway`.
3. Abre el dominio público de `frontend` y prueba login.

## Notas

- Railway usa `RAILWAY_PRIVATE_DOMAIN` para comunicación interna entre servicios.
- El frontend usa Nginx con proxy a `GATEWAY_HOSTPORT`, así que no necesita una URL pública hardcodeada del backend.
- `catalogo-service/render-entrypoint.sh` y `prestamos-service/render-entrypoint.sh` también sirven en Railway porque transforman `DATABASE_URL` a JDBC.