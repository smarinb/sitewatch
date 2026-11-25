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
- **Infra (aplicada):** Terraform en `infra/terraform/` crea una **EC2 t3.micro Ubuntu nueva en us-east-1** con swap, Docker y Docker Compose vía cloud-init, más Elastic IP. El security group abre 80/443 solo a los rangos IP de Cloudflare y SSH solo a la IP del usuario. DNS en **Cloudflare** (provider `cloudflare`): `monitor-api.sergiomarin.dev` apunta a la EC2 y `monitor.sergiomarin.dev` a Pages.
- **Memoria en producción:** la JVM va limitada en el Dockerfile (`-Xmx256m`, SerialGC), PostgreSQL con configuración mínima y swap de 1–2 GB en el host.
- **Observabilidad:** Actuator + Micrometer (`/actuator/prometheus`), y más adelante Prometheus y Grafana.

## Estado actual (actualizado el 2026-11-25)

### Hecho
- **Backend** (Java 21, Spring Boot 4.2, 96 tests con Testcontainers): registro/login con JWT y CRUD de sitios por usuario (un sitio ajeno responde 404); checker programado (`@Scheduled` + hilos virtuales, Apache HttpClient 5) con uptime, tiempo de respuesta, caducidad de SSL y versión de WordPress (meta generator → `feed/` → `readme.html` → `?ver=` de `wp-login.php`, con caché de 12 h); **protección SSRF** (`GuardedDnsResolver`, también en redirecciones; los tests la desactivan con `sitewatch.checker.allow-private-addresses`); alertas por email (SMTP) y Telegram (DOWN tras 2 fallos, RECOVERED con la duración, SSL < 14 días una vez por certificado; `AlertEvaluator` es una función pura); retención de 90 días; `GET /api/checks/latest` y `since` en el histórico; métricas Micrometer.
- **Panel React** (74 tests, Vitest + Testing Library): login/registro, lista de sitios, detalle con gráfica SVG propia (tooltip, teclado, tabla equivalente, caídas como banda), ajustes de alertas, tema claro/oscuro. UI en inglés. Ver `frontend/README.md`.
- **Infra con Terraform (aplicada)**: AWS (VPC propia, EC2 t3.micro con créditos `standard` e IMDSv2, Elastic IP reutilizada de otra instancia, security group con 80/443 solo para Cloudflare y SSH solo para una IP, cloud-init con swap/Docker/secretos desde SSM, SES con DKIM y MAIL FROM propio en sandbox, usuario IAM de SMTP mínimo, OIDC de GitHub y rol de despliegue) y Cloudflare (DNS, 6 registros de SES, proyecto Pages `sitewatch` con `monitor.sergiomarin.dev`, certificado de origen ECDSA de 15 años, regla TLS «Full (strict)» solo para el host de la API). Detalle en `infra/terraform/README.md`. Coste estimado ~13 USD/mes (14,5 con el disco de la otra instancia parada).
- **Despliegue**: `deploy/docker-compose.prod.yml` (PostgreSQL + API + Caddy; solo el proxy publica el 443), `deploy/deploy.sh` (valida argumentos, compose del commit exacto, secretos y certificado desde SSM, `up --wait`, rollback) y jobs de CD en `ci.yml` (imagen a GHCR → SSM por OIDC → comprobación por Cloudflare → panel a Pages), activos con la variable `DEPLOY_ENABLED=true`. **Cada push a `main` que pasa el CI despliega.** El primer despliegue ya corrió bien en la EC2 (contenedores sanos).

### Dónde nos quedamos (2026-11-25)
- **La API pública aún no responde** porque su nombre original, `api.monitor.sergiomarin.dev`, no tiene certificado en el borde de Cloudflare (ver lecciones). Se decidió **`monitor-api.sergiomarin.dev`** y el cambio está en el commit local `030a027`, **sin subir**.
- **Plan de Terraform para ese cambio, sin aplicar** (2 crear, 3 cambiar, 2 destruir: el certificado y su CSR se reemplazan; no toca la EC2, la IP, SES ni Pages). El fichero del plan guardado está en un directorio temporal y puede haber desaparecido: hay que volver a planificar.
- **Orden correcto**: primero `terraform apply` (así el certificado nuevo ya está en SSM) y después `git push` (relanza el despliegue). Luego comprobar `https://monitor-api.sergiomarin.dev/actuator/health` y `https://monitor.sergiomarin.dev`.

### Pendiente, en orden
1. Aplicar el cambio de nombre, subir y comprobar el despliegue de punta a punta (arriba).
2. **Presupuesto de AWS** (20 USD, solo recursos con la etiqueta `Project=sitewatch`): poner `enable_budget = true` cuando la etiqueta aparezca en Billing (`aws ce list-cost-allocation-tags --tag-keys Project`; tarda ~24 h desde la creación de recursos) y aplicar.
3. **SES**: el dominio estaba en `PENDING` (los registros DNS resuelven bien; AWS tarda). Cuando esté verificado, probar `/api/me/notifications/test` en producción.
4. **Telegram en producción**: guardar `TELEGRAM_BOT_TOKEN` en SSM (`/sitewatch/env/TELEGRAM_BOT_TOKEN`, SecureString) y redesplegar; cada usuario guarda su chat id en el panel.
5. **Cuenta de demostración** para recruiters, con sitios de ejemplo (no la web de terceros usada en las pruebas).
6. **Prometheus + Grafana**: la instancia tiene 1 GB de RAM (hoy usa ~346 MB); decidir entre Grafana Cloud gratis y alojarlo en la propia máquina (si no cabe, `t3.small` suma ~7,6 USD/mes). `/actuator/prometheus` está protegido por JWT y bloqueado en el proxy.
7. **Verificación de email en el registro** y solo después pedir a AWS que saque SES del sandbox (hoy cualquiera podría registrar el correo de otra persona).
8. **Copias de seguridad** de PostgreSQL (a S3) y mover el **estado de Terraform** (hoy local, con secretos) a un backend S3 cifrado.
9. README en inglés y diagrama de arquitectura actualizado; limpieza de GitHub y ficha de WordPress freelance (ver `CLAUDE.local.md`).
10. Mejoras: rangos de 7/30 días (requiere agregar datos en el backend), límite de intentos de login, i18n del panel.
11. Fase 2: plugins vulnerables vía API de WPScan y página de estado pública.

### Lecciones (no repetir)
- **El host de la API tiene un solo nivel** bajo la zona: el certificado gratuito de Cloudflare (Universal SSL) cubre `dominio` y `*.dominio`; en un nombre de segundo nivel todo apretón TLS falla en el borde aunque el servidor esté sano.
- **El subject de OIDC de GitHub es el *inmutable*** en repos nuevos (`repo:dueño@<id>/repo@<id>`); la política de confianza del rol debe usar ese formato (variable `github_oidc_subject_prefix`, en `terraform.tfvars`).
- **Desde SSM, el AWS CLI instalado como snap no escribe nada (y sale con 0) si su salida se redirige a un fichero**; hay que capturarla en una variable o una tubería.
- **El token de Terraform necesita «SSL y certificados: Editar»** para crear certificados de origen; los permisos «Editar» no se pueden comprobar con lecturas.
- Un plan guardado deja de valer si cambia el `.terraform.lock.hcl`; `terraform -exclude` no existe en esta versión (se usa `-target` o una variable).
- La etiqueta de costes no existe en Billing hasta ~24 h después de crear recursos etiquetados.

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
