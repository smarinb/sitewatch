# Sitewatch: contexto del proyecto

Monitor de sitios web y WordPress: uptime, tiempo de respuesta, caducidad de SSL y versión de WordPress, con alertas por email y Telegram. Proyecto de portfolio **que debe estar desplegado y accesible en vivo** en https://monitor.sergiomarin.dev.

Idioma de trabajo con el usuario: español. Código, commits y nombres en inglés. README en español (más adelante habrá versión en inglés).

## Decisiones tomadas
- **Backend:** Java 21 + Spring Boot 4.2 (Maven wrapper). Elegido por demanda en Latam y EE. UU. Paquete base `dev.sergiomarin.sitewatch`.
- **Auth:** JWT propio con `spring-boot-starter-security-oauth2-resource-server` (Nimbus, HS256). Registro y login emiten el token. Sin sesiones (STATELESS).
- **BD:** PostgreSQL 17, esquema gestionado por Flyway (`backend/src/main/resources/db/migration`). `ddl-auto: validate`: toda modificación de esquema va en una nueva migración `V<n>__*.sql`, nunca se edita una ya aplicada.
- **Chequeos:** dentro del mismo proceso, con `@Scheduled`. No hay workers ni colas aparte, para que todo quepa en una t3.micro de 1 GB.
- **Frontend:** React 19 + TypeScript + Vite, desplegado como estático en **Cloudflare Pages**.
- **Tests:** JUnit 5 + Testcontainers (PostgreSQL real). Llevan `@Testcontainers(disabledWithoutDocker = true)`: sin Docker se saltan, y en CI se ejecutan.
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) para backend (`./mvnw verify`) y frontend (lint + build).
- **Infra (pendiente):** Terraform en `infra/terraform/` crea una **EC2 t3.micro Ubuntu nueva en us-east-1** con swap, Docker y Docker Compose vía cloud-init, más Elastic IP. El security group abre 80/443 solo a los rangos IP de Cloudflare y SSH solo a la IP del usuario. DNS en **Cloudflare** (provider `cloudflare`): `api.monitor.sergiomarin.dev` apunta a la EC2 y `monitor.sergiomarin.dev` a Pages.
- **Memoria en producción:** la JVM va limitada en el Dockerfile (`-Xmx256m`, SerialGC), PostgreSQL con configuración mínima y swap de 1–2 GB en el host.
- **Observabilidad:** Actuator + Micrometer (`/actuator/prometheus`), y más adelante Prometheus y Grafana.

## Estado actual
- [x] Esqueleto: backend generado con Spring Initializr, `application.yml` configurable por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`), migración V1 (`app_user`, `site`, `site_check`), `SecurityConfig` (solo es público `/actuator/health/**` e `/actuator/info`), Dockerfile multi-stage con usuario no root, `docker-compose.yml` para desarrollo (db + api), CI.
- [x] Frontend: plantilla Vite react-ts sin modificar.
- [x] Registro y login con JWT (`/api/auth/register`, `/login`, `/me`) y CRUD de sitios por usuario (`/api/sites`; un sitio ajeno responde 404). Secreto en `JWT_SECRET` (Base64, ≥32 bytes, sin valor por defecto); los tests usan el perfil `test` y heredan de `IntegrationTest`.
- [ ] **Siguiente:** checker programado: uptime, tiempo de respuesta, SSL y versión de WordPress (meta generator, `/wp-json`, `readme.html`).
- [ ] Alertas por email y Telegram (cambio de estado up→down, SSL a menos de 14 días de caducar).
- [ ] Panel React: login, lista de sitios con estado, detalle con histórico y gráficas.
- [ ] Terraform + despliegue continuo (GitHub Actions → GHCR → EC2).
- [ ] Prometheus + Grafana.
- [ ] Fase 2: plugins vulnerables vía API de WPScan y página de estado pública.

Repo público: https://github.com/smarinb/sitewatch. Pide confirmación antes de hacer push o crear recursos en AWS o Cloudflare.

## Reglas de trabajo
- Antes de `terraform apply` o de cualquier acción que cree recursos con coste, enseña el `plan` y espera la aprobación.
- Nada de secretos en el repo: van en `.env` (ignorado), en GitHub Secrets o en SSM Parameter Store.
- Cada funcionalidad lleva tests. El README (roadmap y diagrama) se actualiza al cerrar cada bloque.
- Commits pequeños con mensajes claros: el historial también es portfolio.

## Comandos
```bash
cp .env.example .env                     # y genera JWT_SECRET (openssl rand -base64 32)
docker compose up -d db                  # PostgreSQL local
cd backend && set -a && . ../.env && set +a && ./mvnw spring-boot:run   # API en :8080
cd backend && ./mvnw verify              # build + tests (Testcontainers necesita Docker)
cd frontend && npm install && npm run dev
cd frontend && npm run lint && npm run build
```

## Entorno de desarrollo (WSL2 Ubuntu)
El proyecto se desarrolla en WSL2 Ubuntu 24.04, dentro del sistema de archivos de Linux (`~/proyectos/sitewatch`), no en `/mnt/c`. Para preparar una máquina nueva: `bash scripts/setup-wsl.sh`. Instala Docker Engine, JDK 21 (SDKMAN), Node 24 (nvm), Terraform, AWS CLI v2, Ansible y utilidades.

Después del script:
1. Cierra y vuelve a abrir la terminal (grupo `docker`, SDKMAN y nvm).
2. `aws configure`: usuario IAM `sergio` (nunca root), región `us-east-1`. Comprueba con `aws sts get-caller-identity`.
3. `git config --global user.name/user.email` y clave SSH para GitHub (`ssh-keygen -t ed25519` y añadirla en GitHub).
4. `gh auth login`.
