output "public_ip" {
  description = "Elastic IP of the instance. Point api.<domain> at it (the Cloudflare step)."
  value       = local.public_ip
}

output "instance_id" {
  value = aws_instance.api.id
}

output "ssh_command" {
  value = "ssh ubuntu@${local.public_ip}"
}

output "session_manager_command" {
  description = "A shell without SSH, if your IP changed."
  value       = "aws ssm start-session --target ${aws_instance.api.id}"
}

# The DNS records SES needs. They are created in Cloudflare (cloudflare.tf); the output is for reference.
output "ses_dns_records" {
  description = "Records SES needs in DNS to verify the domain and sign mail with DKIM."
  value       = values(local.ses_dns_records)
}

output "panel_url" {
  value = "https://${var.domain}"
}

output "api_url" {
  value = "https://${local.api_hostname}"
}

output "pages_project" {
  description = "Cloudflare Pages project the CI deploys the panel to."
  value       = cloudflare_pages_project.panel.name
}

output "ssm_prefix" {
  value = local.ssm_prefix
}

output "github_deploy_role_arn" {
  description = "Role the deploy workflow assumes through OIDC."
  value       = aws_iam_role.github_deploy.arn
}
