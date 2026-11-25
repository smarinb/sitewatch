# Sitewatch

Monitorización de sitios web y WordPress: uptime, tiempo de respuesta, caducidad de SSL y versión de WordPress, con alertas por email/Telegram.

**Demo:** https://monitor.sergiomarin.dev — pulsa **Try the demo** para entrar con una cuenta de solo lectura (el registro está cerrado en la demo pública).

## Stack
| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway |
| Base de datos | PostgreSQL 17 |
| Frontend | React 19, TypeScript, Vite |
| Tests | JUnit 5, Testcontainers |
| CI/CD | GitHub Actions |
| Infra | AWS EC2, Docker Compose, Terraform, Cloudflare (DNS + Pages) |
| Observabilidad | Actuator, Micrometer, Prometheus, Grafana |

## Arquitectura
![Arquitectura de Sitewatch: el navegador carga el panel desde Cloudflare Pages y llama a la API a través de Cloudflare y Caddy; la API guarda en PostgreSQL, comprueba los sitios y avisa por Telegram y SES; Alloy envía métricas a Grafana Cloud, PostgreSQL se vuelca cada noche a S3 y GitHub Actions despliega por SSM](docs/architecture.svg)

Todo cabe en **una EC2 t3.micro de 1 GB**: no hay workers ni colas aparte.

- **Panel y API:** el panel (React) es estático en Cloudflare Pages. Las llamadas a `/api` pasan por Cloudflare (TLS «Full (strict)»), Caddy y llegan a la API de Spring Boot, que guarda en PostgreSQL.
- **Chequeos:** un `@Scheduled` dentro de la propia API comprueba cada sitio en hilos virtuales, con protección SSRF.
- **Alertas:** por Telegram (Bot API) y por email (Amazon SES).
- **Observabilidad:** Grafana Alloy lee `/actuator/prometheus` dentro de la red de Docker y lo envía a Grafana Cloud.
- **Copias:** un temporizador de systemd vuelca PostgreSQL a S3 cada noche; la EC2 solo puede escribir en el bucket.
- **Entrega:** cada push a `main` se prueba, se construye y se despliega por SSM (sin claves de AWS en GitHub). Terraform define AWS y Cloudflare, con el estado en S3.

## Capturas
| Lista de sitios | Detalle con histórico |
|---|---|
| ![Lista de sitios](docs/screenshots/dashboard.png) | ![Detalle de un sitio](docs/screenshots/detail.png) |

*Datos de demostración. La caída de 35 min se dibuja como una banda; el tema oscuro y el móvil también están cubiertos ([captura oscura](docs/screenshots/detail-dark.png)).*

## Desarrollo local
```bash
cp .env.example .env                                   # y rellena JWT_SECRET:
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 32)|" .env
docker compose up -d db                                # PostgreSQL
cd backend && set -a && . ../.env && set +a && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev            # http://localhost:5173
```
Health check: http://localhost:8080/actuator/health

## API
Autenticación stateless con JWT (HS256). Registro y login devuelven el token; el resto de endpoints lo exigen en la cabecera `Authorization: Bearer <token>`. Los errores siguen el formato *problem details* (RFC 9457).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/register` | Crea la cuenta y devuelve un token (`201`; `409` si el email ya existe; `403` si el registro está cerrado) |
| `POST` | `/api/auth/login` | Devuelve un token (`401` si las credenciales no son válidas; `429` tras 5 fallos seguidos del mismo email) |
| `GET` | `/api/auth/config` | Qué ofrece este despliegue: `{"registrationOpen", "demoAvailable"}` |
| `POST` | `/api/auth/demo` | Token de la cuenta de demostración, de solo lectura (`404` si no hay demo) |
| `POST` | `/api/auth/verify-email` | Confirma el email con el token del enlace (`204`; `400` si caducó o ya se usó) |
| `GET` | `/api/auth/me` | Usuario autenticado |
| `GET` | `/api/sites` | Sitios del usuario |
| `POST` | `/api/sites` | Añade un sitio (`name`, `url`, `checkIntervalMinutes` 5–1440 (por defecto 5), `active`) |
| `GET` | `/api/sites/{id}` | Detalle de un sitio |
| `PUT` | `/api/sites/{id}` | Reemplaza un sitio |
| `DELETE` | `/api/sites/{id}` | Borra un sitio |
| `GET` | `/api/sites/{id}/checks?limit=50&since=<ISO>` | Histórico de chequeos, del más reciente al más antiguo (`limit` 1–500; `since` filtra por fecha) |
| `GET` | `/api/checks/latest` | Último chequeo de cada sitio, para la lista del panel |
| `GET` / `PUT` | `/api/me/notifications` | Canales de alerta: `{"emailAlerts": true, "telegramChatId": "123456789"}` |
| `POST` | `/api/me/notifications/test` | Envía una alerta de prueba e indica el resultado por canal |

| `POST` | `/api/me/email-verification` | Reenvía el enlace de confirmación del email (`429` si se pidió hace menos de un minuto) |

Cada usuario solo ve sus sitios: un sitio ajeno responde `404`, igual que uno inexistente. Cada usuario puede vigilar hasta 10 sitios.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"supersecret1"}' | jq -r .accessToken)
curl -s -X POST localhost:8080/api/sites -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Mi blog","url":"https://example.com"}'
```

## Cómo funciona el checker
Cada 30 s, un `@Scheduled` busca los sitios activos cuyo intervalo ha vencido (columna `last_checked_at`) y los comprueba en hilos virtuales de Java 21, con un máximo de 8 a la vez. Cada chequeo guarda una fila en `site_check`:

- **Uptime y estado HTTP:** sigue hasta 5 redirecciones; un estado ≥ 400, un timeout o un error de red o TLS cuentan como caída, con el motivo en `error`.
- **Tiempo de respuesta:** hasta recibir las cabeceras.
- **Caducidad SSL:** fecha `notAfter` del certificado servido por HTTPS. Un certificado caducado o no válido cuenta como caída.
- **Versión de WordPress:** `<meta name="generator">` de la portada. Si el sitio parece WordPress (`/wp-content/`, `/wp-includes/` o la cabecera `Link` de `api.w.org`) pero oculta la versión, prueba `feed/`, `readme.html` y las hojas de estilo del núcleo que carga `wp-login.php` (`?ver=`), que los plugins de seguridad no suelen tocar. Estos sondeos se cachean 12 h por sitio para no generar tráfico extra ni alertar a plugins como Wordfence.

**Protección SSRF:** los chequeos salen del servidor, así que un usuario podría apuntar un sitio a `localhost`, a la red privada o a los metadatos de AWS (`169.254.169.254`). El cliente HTTP (Apache HttpClient 5) usa un `DnsResolver` propio que rechaza cualquier host que resuelva a una dirección no pública (loopback, privadas, link-local, CGNAT, ULA, NAT64 o 6to4 con IPv4 privada…). Como el cliente se conecta exactamente a las direcciones validadas, la comprobación cubre también las redirecciones y el *DNS rebinding*.

El histórico se borra pasados 90 días (cada noche a las 03:30), para que la base de datos no crezca sin límite en una instancia de 1 GB.

Métricas en `/actuator/prometheus`: `sitewatch_checks_seconds{outcome="up|down"}` y `sitewatch_alerts_total{channel, type, result}`. Las lee Grafana Alloy dentro de la red de Docker (ver más abajo); el proxy responde `404` a esa ruta desde fuera.

## Alertas
| Alerta | Cuándo |
|---|---|
| 🔴 Caída | Tras 2 chequeos fallidos seguidos (un fallo suelto no avisa). Una sola vez por incidencia |
| 🟢 Recuperación | En el primer chequeo correcto tras una caída confirmada, con la duración de la caída |
| 🟠 SSL a punto de caducar | Cuando quedan menos de 14 días. Una vez por certificado: si se renueva y el nuevo también está cerca de caducar, vuelve a avisar |

El estado de cada sitio (`UNKNOWN`, `UP` o `DOWN`) se guarda en la tabla `site`, y las alertas se envían después de confirmar la transacción. Si un canal falla, el error se registra y se cuenta en las métricas, pero no afecta al chequeo ni a los demás canales.

- **Email:** a la dirección de la cuenta, **una vez confirmada** con el enlace que se envía al registrarse (un solo uso, caduca a las 24 h y solo se guarda su hash). Hasta entonces no se escribe a esa dirección. Se activa al configurar `SPRING_MAIL_HOST` (y usuario y contraseña si el servidor SMTP los pide). En local, `docker compose up -d mailpit` recoge todos los correos en http://localhost:8025.
- **Telegram:** crea un bot con [@BotFather](https://t.me/BotFather) y pon su token en `TELEGRAM_BOT_TOKEN`. Cada usuario abre un chat con el bot y guarda su *chat id* en `/api/me/notifications`. El token va en la URL de la API de Telegram, así que se elimina de cualquier mensaje de error antes de que llegue a los logs.

## Despliegue
Cada push a `main` que pasa el CI se despliega solo, sin ninguna clave de AWS guardada en GitHub:

```
push → CI (tests, lint, terraform validate, shellcheck)
     → imagen Docker a GHCR (etiqueta sha-<commit>)
     → GitHub obtiene credenciales temporales de AWS por OIDC (solo desde main)
     → SSM Run Command ejecuta deploy/deploy.sh en la EC2
          · descarga el compose del commit exacto, refresca secretos y certificado desde SSM
          · arranca las imágenes nuevas y espera a que estén sanas
          · si no lo están, vuelve a la versión anterior
     → comprueba /actuator/health a través de Cloudflare
     → compila el panel y lo sube a Cloudflare Pages
```
El tráfico entra por Cloudflare (proxy, TLS «Full (strict)» con un certificado de origen) y solo Cloudflare puede llegar a los puertos 80/443 de la EC2. Caddy termina TLS y reenvía a la API; las métricas (`/actuator/prometheus`) no se exponen. El despliegue está apagado hasta que la variable del repositorio `DEPLOY_ENABLED` vale `true`.

## Demo pública
La instancia en vivo está pensada para que se pueda ver, no para usarla como servicio:

- **Registro cerrado** (`REGISTRATION_ENABLED=false`) y una cuenta de demostración (`DEMO_EMAIL`) **de solo lectura**: sus tokens llevan un claim `readOnly` y un filtro rechaza cualquier escritura (`403`), de modo que la interfaz y el servidor están de acuerdo.
- **Límites:** 10 sitios por usuario, un chequeo cada 5 minutos como mínimo y bloqueo de 15 minutos tras 5 fallos de login para un mismo email.
- **SES en sandbox** a propósito: solo puede escribir a direcciones verificadas, así que no sirve para enviar a terceros.

## Observabilidad y copias
- **Métricas:** Grafana Alloy (128 MB) lee la API cada 60 s y escribe en Grafana Cloud (plan gratuito). El panel versionado está en [`deploy/grafana/sitewatch-dashboard.json`](deploy/grafana/sitewatch-dashboard.json).
- **Copias:** `deploy/backup.sh` hace `pg_dump` y lo envía por una tubería a S3 (el volcado nunca toca el disco), cada noche a las 03:00 UTC, con 30 días de retención. El rol de la instancia solo puede añadir objetos. La restauración está probada y documentada en [`infra/terraform/README.md`](infra/terraform/README.md).
- **Presupuesto:** una alerta de AWS Budgets (20 USD/mes, solo recursos con la etiqueta `Project=sitewatch`) avisa por email.

## Roadmap
- [x] Esqueleto: backend, frontend, esquema inicial (Flyway), CI
- [x] Registro/login con JWT
- [x] CRUD de sitios por usuario
- [x] Checker programado: uptime, tiempo de respuesta, SSL, versión de WordPress
- [x] Alertas (email, Telegram)
- [x] Panel React: login, lista de sitios, detalle con gráfica de tiempo de respuesta y ajustes de alertas
- [x] Terraform (AWS y Cloudflare) y despliegue continuo a EC2 y Pages
- [x] Prometheus + Grafana (Alloy → Grafana Cloud)
- [x] Copias de PostgreSQL a S3 y estado de Terraform en S3
- [x] Verificación de email, demo pública de solo lectura y límites de uso
- [ ] Fase 2: plugins vulnerables (WPScan), página de estado pública
