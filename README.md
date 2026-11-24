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
docker compose up -d db          # PostgreSQL
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```
Health check: http://localhost:8080/actuator/health

## Roadmap
- [x] Esqueleto: backend, frontend, esquema inicial (Flyway), CI
- [ ] Registro/login con JWT
- [ ] CRUD de sitios por usuario
- [ ] Checker programado: uptime, tiempo de respuesta, SSL, versión de WordPress
- [ ] Alertas (email, Telegram)
- [ ] Panel React con estado e históricos
- [ ] Terraform + despliegue continuo a EC2
- [ ] Prometheus + Grafana
- [ ] Fase 2: plugins vulnerables (WPScan), página de estado pública
