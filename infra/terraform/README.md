# Infraestructura (Terraform)

Crea en AWS el servidor de Sitewatch y todo lo que necesita para enviar emails, y en Cloudflare los registros DNS y el proyecto de Pages. **Todavía no despliega la aplicación**: eso lo hace el CI en el siguiente bloque.

## Qué crea
| Recurso | Para qué |
|---|---|
| VPC propia, subred pública y gateway | Aislada de cualquier otro proyecto de la cuenta |
| EC2 `t3.micro`, Ubuntu 24.04, disco gp3 de 20 GB cifrado | Servidor de la API y de PostgreSQL |
| Elastic IP | IP fija a la que apuntará `api.monitor.sergiomarin.dev`. Nueva, o una que ya tengas con `existing_eip_allocation_id` |
| Security group | **80/443 solo desde los rangos de Cloudflare** (se descargan de cloudflare.com), **SSH solo desde tu IP** |
| cloud-init | Swap de 2 GB, Docker y Compose, rotación de logs y lectura de secretos desde SSM |
| Rol de instancia | Solo puede leer sus parámetros de SSM; permite Session Manager como alternativa a SSH |
| SES: dominio `monitor.sergiomarin.dev` | Verificado con DKIM y dominio MAIL FROM propio (`mail.monitor…`) para que SPF alinee |
| Usuario IAM de SMTP | Solo puede enviar desde `alerts@monitor.sergiomarin.dev` |
| SSM Parameter Store (SecureString) | `JWT_SECRET`, `DB_PASSWORD` y credenciales SMTP, generados por Terraform |
| Cloudflare: `api.monitor…` | Registro A con proxy hacia la Elastic IP |
| Cloudflare: registros de SES | 3 CNAME de DKIM, MX y TXT (SPF) del dominio MAIL FROM, y DMARC |
| Cloudflare Pages | Proyecto `sitewatch` (subida directa desde el CI), dominio `monitor.sergiomarin.dev` y su CNAME |
| Cloudflare: certificado de origen | Certificado ECDSA de 15 años para `api.monitor…`, generado por Terraform y guardado en SSM (`/sitewatch/tls/`). Solo lo reconoce Cloudflare |
| Cloudflare: regla de configuración | TLS «Full (strict)» **solo** para `api.monitor…`; el modo de la zona no cambia |
| OIDC de GitHub y rol `sitewatch-github-deploy` | El CI despliega sin claves de AWS: solo puede ejecutar comandos en esta instancia y solo desde `main` |
| AWS Budgets | Aviso al 80 % del gasto real y al 100 % del previsto, filtrado por la etiqueta `Project=sitewatch` |

## Decisiones
- **`cpu_credits = "standard"`:** las t3 vienen en modo *unlimited*, que cobra aparte los picos de CPU. En *standard* la instancia se limita en lugar de aumentar la factura.
- **IMDSv2 obligatorio con un solo salto:** los contenedores no pueden pedir las credenciales de la instancia.
- **Secretos en SSM, nunca en el repo ni en una imagen.** El nombre del parámetro es el de la variable de entorno (`/sitewatch/env/JWT_SECRET`). `TELEGRAM_BOT_TOKEN` no lo gestiona Terraform, para que no acabe en el estado (ver abajo).
- **El estado contiene secretos** (contraseñas generadas y la clave SMTP). Es local y está en `.gitignore`. Si lo pierdes, Terraform deja de conocer los recursos. Antes de trabajar en equipo o desde otra máquina habría que moverlo a un backend S3 cifrado.
- **Reutilizar una Elastic IP existente** (`existing_eip_allocation_id`): Terraform solo la asocia a la instancia, con `allow_reassociation`, y no la gestiona. Se la quita a la instancia que la tuviera, y un `terraform destroy` no la libera: queda tuya y sin asociar (sigue facturando hasta que la asocies o la liberes).
- **Los rangos de Cloudflare se leen al hacer `plan`.** Si Cloudflare los cambia, el siguiente `plan` lo muestra como diferencia.

## Coste aproximado
Precios de lista en us-east-1, a verificar con la calculadora de AWS:

| Concepto | USD/mes |
|---|---|
| EC2 `t3.micro` (0,0104 USD/h) | ~7,6 |
| Disco gp3 de 20 GB | ~1,6 |
| IPv4 pública (0,005 USD/h; AWS la cobra aunque esté asociada). Si reutilizas una Elastic IP que ya pagabas, no suma | ~3,7 |
| SES (0,10 USD por 1.000 emails; en sandbox el máximo son 200 al día) | < 1 |
| **Total** | **~13** |

El presupuesto está en 20 USD (`monthly_budget_usd`) y solo cuenta los recursos con la etiqueta `Project=sitewatch`. La etiqueta de costes tarda hasta 24 h en empezar a registrar datos.

## Cloudflare
Solo **añade** registros y un proyecto: los registros que ya tenga la zona (la web, el correo, Resend) no se gestionan aquí y nunca se tocan. El token de API va en la variable `CLOUDFLARE_API_TOKEN`, nunca en el repo ni en `terraform.tfvars`:

```bash
set -a; . ~/.config/sitewatch/cloudflare.env; set +a   # fichero 600 con CLOUDFLARE_API_TOKEN=...
```
Permisos del token: zona `sergiomarin.dev` con DNS (editar), Zona (leer), Configuración de zona (editar) y Reglas de configuración (editar); cuenta con Cloudflare Pages (editar). El id de la zona y de la cuenta van en `terraform.tfvars`.

Todavía **no** se configura el modo TLS «Full (strict)» ni el certificado de origen: se hace junto con el proxy de la EC2 en el bloque de despliegue, para no dejar `api.monitor` devolviendo un 526 mientras no haya nada que servir. Y será solo para `api.monitor` (regla de configuración), sin cambiar el modo de toda la zona, que usa también tu web.

## OIDC de GitHub: el nombre del repositorio en el token
Los repositorios nuevos usan el *subject inmutable*: el token no lleva `repo:dueño/nombre` sino `repo:dueño@<id>/nombre@<id>`, con los identificadores numéricos, para que nadie pueda suplantar el repositorio si se renombra o se borra. La política de confianza del rol tiene que usar ese mismo formato o AWS rechaza el rol con «Not authorized to perform sts:AssumeRoleWithWebIdentity». Léelo con `gh api repos/DUEÑO/REPO/actions/oidc/customization/sub` y ponlo en `github_oidc_subject_prefix` de `terraform.tfvars` (no hace falta que vaya al repositorio).

## Uso
```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # tu IP con /32 y tu email
terraform init
terraform plan -out=plan.tfplan                # revisa qué se va a crear
terraform apply plan.tfplan                    # solo tras revisar el plan
```
Después de aplicar:
1. **Confirma el email de SES.** AWS envía un enlace de verificación a `alert_email`. Sin él, la cuenta (en *sandbox*) no puede enviarte alertas.
2. **Crea los registros DNS** de `terraform output ses_dns_records` (3 CNAME de DKIM, MX y TXT del dominio MAIL FROM, y DMARC). Hasta entonces SES no verifica el dominio. Esto lo automatiza el paso de Cloudflare.
3. **Token de Telegram** (opcional): `aws ssm put-parameter --name /sitewatch/env/TELEGRAM_BOT_TOKEN --type SecureString --value '<token>'` y en el servidor `sudo sitewatch-fetch-secrets`.
4. **Comprobar el arranque:** `ssh ubuntu@<ip>` y `cat /var/lib/sitewatch/bootstrap-done`. El registro está en `/var/log/cloud-init-output.log`. Si tu IP cambió: `aws ssm start-session --target <instance_id>`.

**Presupuesto opcional (`enable_budget`):** la etiqueta de costes tarda ~24 h en existir. Deja `enable_budget = false` en el primer `apply` y ponlo a `true` después; el presupuesto no se crea hasta entonces.

**Primer `apply` en una cuenta sin la etiqueta de costes activada:** la etiqueta `Project` solo aparece en Billing hasta unas 24 h después de crear recursos etiquetados, y hasta entonces `aws_ce_cost_allocation_tag` falla con «Tag keys not found: Project» (y el presupuesto, que depende de ella, no se crea). Es lo esperado: el resto queda creado, y basta repetir `terraform apply` pasado ese tiempo.

`terraform destroy` lo elimina todo. Las identidades de SES y el parámetro de presupuesto se pueden recrear sin problema.

## SES y el *sandbox*
Una cuenta nueva solo puede enviar a direcciones verificadas y 200 emails al día. Es suficiente para tus propias alertas. Para escribir a cualquier usuario hay que pedir acceso a producción a AWS, y antes conviene verificar el email en el registro de la aplicación: ahora mismo cualquiera podría dar de alta el correo de otra persona y hacer que SES le envíe alertas, y los rebotes o quejas pueden hacer que AWS suspenda el envío.
