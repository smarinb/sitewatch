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
                                              └── @Scheduled checkers ──► sitios monitorizados
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

Cada usuario solo ve sus sitios: un sitio ajeno responde `404`, igual que uno inexistente.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"supersecret1"}' | jq -r .accessToken)
curl -s -X POST localhost:8080/api/sites -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Mi blog","url":"https://example.com"}'
```

## Roadmap
- [x] Esqueleto: backend, frontend, esquema inicial (Flyway), CI
- [x] Registro/login con JWT
- [x] CRUD de sitios por usuario
- [ ] Checker programado: uptime, tiempo de respuesta, SSL, versión de WordPress
- [ ] Alertas (email, Telegram)
- [ ] Panel React con estado e históricos
- [ ] Terraform + despliegue continuo a EC2
- [ ] Prometheus + Grafana
- [ ] Fase 2: plugins vulnerables (WPScan), página de estado pública
