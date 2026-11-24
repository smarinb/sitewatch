# Infra (pendiente)

Terraform para la instancia de producción:
- EC2 t3.micro Ubuntu en us-east-1 con swap, Docker y Docker Compose (cloud-init)
- Security group: 80/443 solo desde rangos de Cloudflare, SSH solo desde tu IP
- Elastic IP + registro DNS `api.monitor.sergiomarin.dev` en Cloudflare (provider cloudflare)
- Frontend estático en Cloudflare Pages
