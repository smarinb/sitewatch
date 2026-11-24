# Infraestructura (Terraform)

Crea en AWS el servidor de Sitewatch y todo lo que necesita para enviar emails. **Todavía no despliega la aplicación**: eso lo hace el CI en el siguiente bloque. El DNS de Cloudflare y Cloudflare Pages también quedan para el paso siguiente.

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

**Primer `apply` en una cuenta sin la etiqueta de costes activada:** la etiqueta `Project` solo aparece en Billing hasta unas 24 h después de crear recursos etiquetados, y hasta entonces `aws_ce_cost_allocation_tag` falla con «Tag keys not found: Project» (y el presupuesto, que depende de ella, no se crea). Es lo esperado: el resto queda creado, y basta repetir `terraform apply` pasado ese tiempo.

`terraform destroy` lo elimina todo. Las identidades de SES y el parámetro de presupuesto se pueden recrear sin problema.

## SES y el *sandbox*
Una cuenta nueva solo puede enviar a direcciones verificadas y 200 emails al día. Es suficiente para tus propias alertas. Para escribir a cualquier usuario hay que pedir acceso a producción a AWS, y antes conviene verificar el email en el registro de la aplicación: ahora mismo cualquiera podría dar de alta el correo de otra persona y hacer que SES le envíe alertas, y los rebotes o quejas pueden hacer que AWS suspenda el envío.
