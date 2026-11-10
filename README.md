# Money Manager · Backend

API REST de **Money Manager**, una aplicación para llevar tus ingresos y gastos, poner presupuestos y ahorrar para objetivos.

- Web: <https://money-manager-cristian.netlify.app>
- API en producción: `https://money-manager-t0yw.onrender.com/api/v1.0`
- Frontend: [Money-Manager-Front-End](https://github.com/CristianFiguerolaVicedo/Money-Manager-Front-End)

## Qué hace

- Cuentas con activación por email, inicio de sesión con JWT, recuperación de contraseña, límite de intentos y borrado de cuenta.
- Ingresos y gastos por categorías, con nota y archivos adjuntos (tickets o facturas en imagen o PDF).
- Movimientos recurrentes (semanales, mensuales o anuales) que se crean solos.
- Presupuestos mensuales por categoría y objetivos de ahorro con aportaciones.
- Panel con totales, los últimos 6 meses y los gastos por categoría.
- Búsqueda con filtros y paginación, exportación a Excel (descarga o email) e importación desde CSV.
- Emails opcionales (recordatorio diario, resumen diario e informe mensual) con baja en un clic.

## Tecnología

| | |
|---|---|
| Lenguaje y framework | Java 21, Spring Boot 3.5 (Web, Security, Data JPA) |
| Base de datos | PostgreSQL ([Neon](https://neon.com) en producción), migraciones con Flyway |
| Autenticación | JWT (HS256) sin estado |
| Emails | API HTTP de [Brevo](https://www.brevo.com) |
| Excel | Apache POI |
| Tests | JUnit 5, Spring Boot Test y MockMvc contra un PostgreSQL real |
| Despliegue | Docker en [Render](https://render.com) (plan gratuito) |

## Estructura

```
src/main/java/com/cristian/moneymanager/
├── config/        Seguridad (CORS, rutas públicas, filtro JWT)
├── controller/    Endpoints REST
├── dto/           Objetos que entran y salen de la API
├── email/         Plantilla HTML común de los emails
├── entity/        Tablas (JPA)
├── exception/     Errores → JSON {"message": "..."} en español
├── repository/    Consultas (Spring Data)
├── security/      Filtro JWT y límite de peticiones por IP
├── service/       Lógica de negocio
└── util/          JWT, validaciones, meses
src/main/resources/
├── application.properties        Configuración común
├── application-prod.properties   PostgreSQL, Flyway y logs de producción
└── db/migration/                 V1, V2… (esquema versionado)
```

Todas las rutas cuelgan de `/api/v1.0`. Son públicas `/health`, `/status`, `/register`, `/activate`, `/login`, `/forgot-password`, `/reset-password`, `/resend-activation` y `/unsubscribe`; `/internal/jobs/*` se protege con su propio secreto. El resto necesita la cabecera `Authorization: Bearer <token>`.

## Variables de entorno

| Variable | Obligatoria | Para qué |
|---|---|---|
| `POSTGRESQL_URL` | Sí | `jdbc:postgresql://host/base?sslmode=require` |
| `POSTGRESQL_USERNAME` / `POSTGRESQL_PASSWORD` | Sí | Usuario y contraseña de la base de datos |
| `JWT_SECRET` | Sí en producción | Clave en Base64 de al menos 32 bytes. Si falta se genera una al arrancar y las sesiones se pierden en cada reinicio. Para crear una: `openssl rand -base64 48` |
| `MONEY_MANAGER_FRONTEND_URL` | Sí | URL de la web, para los enlaces de los emails |
| `BREVO_API_KEY` | Para enviar emails | Clave de la API de Brevo |
| `BREVO_SENDER_EMAIL` | No | Remitente verificado en Brevo |
| `CORS_ALLOWED_ORIGINS` | No | Orígenes permitidos (por defecto la web, sus vistas previas de Netlify y `localhost`) |
| `JOBS_SECRET` | Para los emails programados | Debe coincidir con el secreto `JOBS_SECRET` del repositorio en GitHub |

## Ejecutar en local

Necesitas Java 21 y Docker (para PostgreSQL).

```bash
docker run -d --name mm-db -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=moneymanager -p 5432:5432 postgres:16

export POSTGRESQL_URL=jdbc:postgresql://localhost:5432/moneymanager
export POSTGRESQL_USERNAME=postgres
export POSTGRESQL_PASSWORD=dev
export MONEY_MANAGER_FRONTEND_URL=http://localhost:5173

./mvnw spring-boot:run
```

La API queda en `http://localhost:8080/api/v1.0` (comprueba `GET /health`). Flyway crea las tablas al arrancar. Sin `BREVO_API_KEY` los emails no se envían, así que para activar una cuenta de prueba pon `is_active = true` en `tbl_profiles`.

## Tests

```bash
./mvnw verify
```

Los tests unitarios se ejecutan siempre. Los de extremo a extremo (cuentas, presupuestos, recurrentes, importación, adjuntos…) arrancan la aplicación entera y solo se ejecutan si `POSTGRESQL_URL` apunta a un PostgreSQL; en local puedes usar el contenedor de arriba. En GitHub Actions (`.github/workflows/ci.yml`) se ejecutan todos en cada PR con un PostgreSQL de servicio.

## Base de datos y migraciones

El esquema está versionado en `src/main/resources/db/migration` y Hibernate solo **valida** que las entidades coinciden (`ddl-auto=validate`). Para cambiar una tabla:

1. Cambia la entidad.
2. Añade `V<siguiente>__descripcion.sql` con el `ALTER`/`CREATE` correspondiente. No edites nunca una migración que ya está en producción.
3. Los tests de CI aplican todas las migraciones a una base vacía y fallan si no coinciden con las entidades.

| Versión | Contenido |
|---|---|
| V1 | Esquema inicial (perfiles, categorías, ingresos, gastos) |
| V2 | Preferencias de email, presupuestos y registro de tareas |
| V3 | Movimientos recurrentes |
| V4 | Objetivos de ahorro |
| V5 | Notas y archivos adjuntos |

## Tareas programadas

Render gratuito duerme el servidor cuando nadie lo usa, así que los temporizadores internos no sirven. Las tareas las lanza GitHub Actions (`.github/workflows/scheduled-emails.yml`): primero despierta el servidor y después llama a `POST /internal/jobs/{tarea}` con la cabecera `X-Jobs-Secret`.

| Tarea | Hora (UTC) | Qué hace |
|---|---|---|
| `recurring` | 05:00 | Crea los movimientos recurrentes pendientes |
| `daily-reminder` | 19:00 | Recordatorio a quien lo activó y no apuntó nada ese día |
| `daily-summary` | 20:30 | Resumen de los gastos del día |
| `monthly-report` | 08:00 el día 1 | Informe del mes anterior |

Cada tarea de email se ejecuta como mucho una vez al día, aunque se repita la llamada. También se pueden lanzar a mano desde *Actions → Scheduled jobs → Run workflow*.

> GitHub pausa las tareas programadas de un repositorio que lleva 60 días sin cambios. Si dejan de ejecutarse, vuelve a activarlas en la pestaña *Actions*.

## Despliegue

Render construye la imagen con el `Dockerfile` y despliega solo cada vez que se fusiona algo en `main`. Las variables de entorno se configuran en *Render → Money-Manager → Environment*. En el primer arranque tras una migración nueva, el log muestra `Successfully applied 1 migration`.

## Copias de seguridad

El plan gratuito de Neon solo permite volver atrás 6 horas. Por eso `.github/workflows/backup.yml` hace cada noche (03:17 UTC) una copia completa con `pg_dump`, comprueba que se puede restaurar en un PostgreSQL vacío, la **cifra con AES-256** y la guarda 30 días como artefacto de GitHub Actions. Va cifrada porque el repositorio es público.

Necesita dos secretos en *Settings → Secrets and variables → Actions*:

- `NEON_DATABASE_URL`: la cadena de conexión **directa** de Neon (sin `-pooler`), en formato `postgresql://usuario:contraseña@ep-....neon.tech/neondb?sslmode=require`.
- `BACKUP_PASSPHRASE`: una frase larga y aleatoria (`openssl rand -base64 32`). **Guárdala en un gestor de contraseñas**: sin ella no se pueden abrir las copias.

### Restaurar una copia

1. En *Actions → Database backup*, abre una ejecución y descarga el artefacto (`money-manager-AAAA-MM-DD.dump.gpg`, dentro de un `.zip`).
2. Descífralo:
   ```bash
   gpg --decrypt --output money-manager.dump money-manager-AAAA-MM-DD.dump.gpg
   ```
3. Restáuralo. Lo más seguro es hacerlo en una base nueva (por ejemplo, una rama nueva de Neon) y apuntar después `POSTGRESQL_URL` a ella:
   ```bash
   pg_restore --no-owner --no-privileges --dbname "postgresql://usuario:contraseña@host/base?sslmode=require" money-manager.dump
   ```
   Para sobrescribir la base actual añade `--clean --if-exists` (borra lo que haya).

`pg_restore` debe ser de la versión 18 o superior (la de Neon). Si no la tienes instalada: `docker run --rm -it -v "$PWD:/b" postgres:18 pg_restore ... /b/money-manager.dump`.
