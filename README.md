# Sitewatch

Monitorización de sitios web y WordPress: uptime, tiempo de respuesta, caducidad de SSL y versión de WordPress, con alertas por email/Telegram.

**Demo:** https://monitor.sergiomarin.dev (próximamente)

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
```
 Navegador ──► Cloudflare Pages (React SPA)
     │
     └──► Cloudflare (proxy, TLS) ──► EC2 ──► Spring Boot API ──► PostgreSQL
                                              │
                                              └── @Scheduled checker (hilos virtuales,
                                                  DNS con protección SSRF) ──► sitios monitorizados
```

## Desarrollo local
```bash
cp .env.example .env                                   # y rellena JWT_SECRET:
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 32)|" .env
docker compose up -d db                                # PostgreSQL
cd backend && set -a && . ../.env && set +a && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```
Health check: http://localhost:8080/actuator/health

## API
Autenticación stateless con JWT (HS256). Registro y login devuelven el token; el resto de endpoints lo exigen en la cabecera `Authorization: Bearer <token>`. Los errores siguen el formato *problem details* (RFC 9457).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/register` | Crea la cuenta y devuelve un token (`201`; `409` si el email ya existe) |
| `POST` | `/api/auth/login` | Devuelve un token (`401` si las credenciales no son válidas) |
| `GET` | `/api/auth/me` | Usuario autenticado |
| `GET` | `/api/sites` | Sitios del usuario |
| `POST` | `/api/sites` | Añade un sitio (`name`, `url`, `checkIntervalMinutes` 1–1440 (por defecto 5), `active`) |
| `GET` | `/api/sites/{id}` | Detalle de un sitio |
| `PUT` | `/api/sites/{id}` | Reemplaza un sitio |
| `DELETE` | `/api/sites/{id}` | Borra un sitio |
| `GET` | `/api/sites/{id}/checks?limit=50` | Histórico de chequeos, del más reciente al más antiguo (`limit` 1–500) |
| `GET` / `PUT` | `/api/me/notifications` | Canales de alerta: `{"emailAlerts": true, "telegramChatId": "123456789"}` |
| `POST` | `/api/me/notifications/test` | Envía una alerta de prueba e indica el resultado por canal |

Cada usuario solo ve sus sitios: un sitio ajeno responde `404`, igual que uno inexistente.

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

Métricas en `/actuator/prometheus`: `sitewatch_checks_seconds{outcome="up|down"}` y `sitewatch_alerts_total{channel, type, result}`.

## Alertas
| Alerta | Cuándo |
|---|---|
| 🔴 Caída | Tras 2 chequeos fallidos seguidos (un fallo suelto no avisa). Una sola vez por incidencia |
| 🟢 Recuperación | En el primer chequeo correcto tras una caída confirmada, con la duración de la caída |
| 🟠 SSL a punto de caducar | Cuando quedan menos de 14 días. Una vez por certificado: si se renueva y el nuevo también está cerca de caducar, vuelve a avisar |

El estado de cada sitio (`UNKNOWN`, `UP` o `DOWN`) se guarda en la tabla `site`, y las alertas se envían después de confirmar la transacción. Si un canal falla, el error se registra y se cuenta en las métricas, pero no afecta al chequeo ni a los demás canales.

- **Email:** a la dirección de la cuenta. Se activa al configurar `SPRING_MAIL_HOST` (y usuario y contraseña si el servidor SMTP los pide). En local, `docker compose up -d mailpit` recoge todos los correos en http://localhost:8025.
- **Telegram:** crea un bot con [@BotFather](https://t.me/BotFather) y pon su token en `TELEGRAM_BOT_TOKEN`. Cada usuario abre un chat con el bot y guarda su *chat id* en `/api/me/notifications`. El token va en la URL de la API de Telegram, así que se elimina de cualquier mensaje de error antes de que llegue a los logs.

## Roadmap
- [x] Esqueleto: backend, frontend, esquema inicial (Flyway), CI
- [x] Registro/login con JWT
- [x] CRUD de sitios por usuario
- [x] Checker programado: uptime, tiempo de respuesta, SSL, versión de WordPress
- [x] Alertas (email, Telegram)
- [ ] Panel React con estado e históricos
- [ ] Terraform + despliegue continuo a EC2
- [ ] Prometheus + Grafana
- [ ] Fase 2: plugins vulnerables (WPScan), página de estado pública
