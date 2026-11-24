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
- **Infra (escrita, sin aplicar):** Terraform en `infra/terraform/` crea una **EC2 t3.micro Ubuntu nueva en us-east-1** con swap, Docker y Docker Compose vía cloud-init, más Elastic IP. El security group abre 80/443 solo a los rangos IP de Cloudflare y SSH solo a la IP del usuario. DNS en **Cloudflare** (provider `cloudflare`): `monitor-api.sergiomarin.dev` apunta a la EC2 y `monitor.sergiomarin.dev` a Pages.
- **Memoria en producción:** la JVM va limitada en el Dockerfile (`-Xmx256m`, SerialGC), PostgreSQL con configuración mínima y swap de 1–2 GB en el host.
- **Observabilidad:** Actuator + Micrometer (`/actuator/prometheus`), y más adelante Prometheus y Grafana.

## Estado actual
- [x] Esqueleto: backend generado con Spring Initializr, `application.yml` configurable por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`), migración V1 (`app_user`, `site`, `site_check`), `SecurityConfig` (solo es público `/actuator/health/**` e `/actuator/info`), Dockerfile multi-stage con usuario no root, `docker-compose.yml` para desarrollo (db + api), CI.
- [x] Frontend (plantilla de Vite sustituida): ver el bloque del panel más abajo.
- [x] Registro y login con JWT (`/api/auth/register`, `/login`, `/me`) y CRUD de sitios por usuario (`/api/sites`; un sitio ajeno responde 404). Secreto en `JWT_SECRET` (Base64, ≥32 bytes, sin valor por defecto); los tests usan el perfil `test` y heredan de `IntegrationTest`.
- [x] Checker programado (paquete `check`): `CheckScheduler` (cada 30 s) → `CheckRunner` (hilos virtuales, máx. 8) → `SiteChecker` (Apache HttpClient 5) → `CheckRecorder`. Migración V2 añade `site.last_checked_at`. Versión de WP: meta generator; si el sitio parece WP pero la oculta, `feed/`, `readme.html` y `?ver=` de los CSS del núcleo en `wp-login.php`, con caché de 12 h por sitio (`/wp-json` no expone la versión; solo sirve como indicio vía la cabecera `Link: api.w.org`). Histórico en `GET /api/sites/{id}/checks`.
  - **SSRF:** `GuardedDnsResolver` rechaza hosts que resuelvan a direcciones no públicas (también en redirecciones). Los tests lo desactivan con `sitewatch.checker.allow-private-addresses: true` en el perfil `test`; en producción debe quedar en `false`.
- [x] Alertas (paquete `alert`). Migración V3: `site.status`/`consecutive_failures`/`down_since`/`ssl_alerted_expiry` y `app_user.email_alerts`/`telegram_chat_id`. `AlertEvaluator` es una función pura: DOWN tras 2 fallos seguidos, RECOVERED con la duración de la caída, SSL una vez por certificado con menos de 14 días. `CheckRecorder` devuelve los eventos y `CheckRunner` los envía después del commit con `AlertDispatcher`, que recorre los `AlertChannel` (email y Telegram). SMTP mediante `SPRING_MAIL_*` (en dev, Mailpit en compose); Telegram con `TELEGRAM_BOT_TOKEN` y el chat id de cada usuario. Ajustes en `/api/me/notifications` (+ `/test`). Los textos de las alertas están en inglés.
- [x] Retención: los chequeos de más de 90 días se borran cada noche (`sitewatch.checker.retention`).
- [x] Panel React (`frontend/`): login/registro, lista de sitios con estado, detalle (uptime, media y p95, SSL, WordPress, gráfica de tiempo de respuesta con rango 1 h / 6 h / 24 h) y ajustes de alertas. Cliente de API tipado, JWT en `localStorage`, `react-router`, gráfica en SVG propio (sin librería) con teclado y vista de tabla, tema claro/oscuro. UI en inglés. Detalles en `frontend/README.md`. Backend: `GET /api/checks/latest` y `since` en el histórico. 74 tests (Vitest + Testing Library); `oxlint` y `tsc` sin avisos.
- [x] Terraform del lado de AWS escrito y con `plan` revisado (73 recursos nuevos, 0 cambios): VPC propia, EC2 t3.micro (créditos `standard`, IMDSv2), Elastic IP (reutiliza una existente por decisión del usuario), security group (80/443 solo Cloudflare, SSH solo tu IP), cloud-init (swap, Docker, secretos desde SSM), SES (dominio con DKIM + MAIL FROM, SMTP con IAM mínimo, sandbox), parámetros SSM y AWS Budgets por etiqueta (20 USD). **Aplicado el 2026-11-24 con aprobación del usuario: 71 de 73 recursos creados** (EC2 `i-0bf9608d7470c97fb` en marcha y verificada por SSH: swap, Docker y secretos de SSM OK). Faltan la etiqueta de costes y el presupuesto: `UpdateCostAllocationTagsStatus` falla con «Tag keys not found: Project» hasta que la etiqueta aparece en Billing (hasta ~24 h tras crear recursos etiquetados). Hay que volver a ejecutar `terraform apply` pasado ese tiempo. Coste estimado ~13 USD/mes. Detalle en `infra/terraform/README.md`.
- [x] Terraform de Cloudflare escrito (`cloudflare.tf`, provider v5): `monitor-api` (A con proxy), 6 registros de SES, proyecto Pages `sitewatch` (subida directa) y `monitor.sergiomarin.dev`. **Aplicado el 2026-11-24 con aprobación del usuario: 10 recursos creados, 0 deriva** (la zona pasó de 15 a 23 registros; los existentes no se tocaron). Pages `sitewatch` con `monitor.sergiomarin.dev` activo; `monitor-api` resuelve vía Cloudflare, pero la EC2 aún no sirve la API. Los 6 registros de SES resuelven bien en DNS público; la identidad de SES seguía en PENDING (a la espera de AWS). Para volver a planificar solo Cloudflare se usó `-target` mientras el presupuesto de AWS esperaba la etiqueta de costes. Token en `~/.config/sitewatch/cloudflare.env` (fuera del repo; `set -a; . ...; set +a`). Los permisos «Editar» no se pueden comprobar sin escribir: un 403 al aplicar diría cuál falta. TLS «Full (strict)» y el certificado de origen se dejan para el despliegue.
- [x] Despliegue escrito y probado en local (pila completa con TLS, 26 s hasta estar sana, ~346 MB): `deploy/docker-compose.prod.yml` (db + api + Caddy; solo el proxy publica el 443), `deploy/Caddyfile`, `deploy/deploy.sh` (valida argumentos, compose del commit exacto, secretos y certificado desde SSM, `up --wait`, rollback) y jobs de CD en `ci.yml` (imagen a GHCR → SSM por OIDC → comprobación por Cloudflare → panel a Pages), apagados con `vars.DEPLOY_ENABLED`. Terraform nuevo (`tls.tf`, `deploy.tf`): certificado de origen, regla «Full (strict)» solo para `monitor-api`, OIDC + rol de despliegue; `enable_budget` para el presupuesto. **Aplicado el 2026-11-25 con aprobación del usuario** (los 9 recursos + el cambio en la política de lectura de SSM). El primer intento falló con 401 en `cloudflare_origin_ca_certificate` porque el token de Terraform no tenía «SSL y certificados: Editar»; el usuario lo añadió y los 3 recursos restantes se crearon. Verificado: certificado ECDSA de origen válido hasta 2041 con su clave en SSM, regla «strict» solo para `monitor-api…`, modo SSL de la zona sin cambios (`full`), sin deriva.
- **Lección (2026-11-25): la API se llama `monitor-api.sergiomarin.dev`, no `api.monitor…`.** El certificado gratuito de Cloudflare (Universal SSL) solo cubre `sergiomarin.dev` y `*.sergiomarin.dev`: un subdominio de segundo nivel no tiene certificado en el borde y todo apretón TLS falla. También: el subject OIDC de GitHub es el *inmutable* (`repo:dueño@id/repo@id`, en `terraform.tfvars`), y desde SSM el AWS CLI (snap) no escribe nada si su salida se redirige a un fichero (se captura en variable).
- [ ] **Siguiente:** aplicar ese plan; crear un token de Cloudflare solo de Pages para GitHub; variables y secretos del repo (`AWS_DEPLOY_ROLE_ARN`, `EC2_INSTANCE_ID`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `DEPLOY_ENABLED`); primer despliegue y hacer público el paquete de GHCR; luego activar `enable_budget`
- [ ] (antes) completar el presupuesto con un segundo `terraform apply` (≥24 h después); después Cloudflare (DNS de la API y de SES, que sigue PENDING hasta tener los registros; Pages) y el despliegue (el panel va a Cloudflare Pages con `VITE_API_URL`; la API necesita `CORS_ALLOWED_ORIGINS=https://monitor.sergiomarin.dev`, SMTP real y `TELEGRAM_BOT_TOKEN`).
- [ ] Despliegue continuo (GitHub Actions → GHCR → EC2) y Pages.
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
cd frontend && npm run lint && npm test && npm run build
```

## Entorno de desarrollo (WSL2 Ubuntu)
El proyecto se desarrolla en WSL2 Ubuntu 24.04, dentro del sistema de archivos de Linux (`~/proyectos/sitewatch`), no en `/mnt/c`. Para preparar una máquina nueva: `bash scripts/setup-wsl.sh`. Instala Docker Engine, JDK 21 (SDKMAN), Node 24 (nvm), Terraform, AWS CLI v2, Ansible y utilidades.

Después del script:
1. Cierra y vuelve a abrir la terminal (grupo `docker`, SDKMAN y nvm).
2. `aws configure`: usuario IAM `sergio` (nunca root), región `us-east-1`. Comprueba con `aws sts get-caller-identity`.
3. `git config --global user.name/user.email` y clave SSH para GitHub (`ssh-keygen -t ed25519` y añadirla en GitHub).
4. `gh auth login`.
