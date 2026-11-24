# DNS and Pages in Cloudflare. Only adds records and a project: the zone's existing records
# (the portfolio, Hostinger mail, Resend) are not managed here and are never touched.

locals {
  api_hostname = "api.${var.domain}"
}

# ---- the API: proxied by Cloudflare, so TLS and DDoS protection come with it ----
resource "cloudflare_dns_record" "api" {
  zone_id = var.cloudflare_zone_id
  name    = local.api_hostname
  type    = "A"
  content = local.public_ip
  proxied = true
  ttl     = 1 # automatic
  comment = "Sitewatch API (Terraform)"
}

# ---- SES: DKIM, custom MAIL FROM and DMARC. DNS only: these must resolve to the real values. ----
resource "cloudflare_dns_record" "ses" {
  for_each = local.ses_dns_records

  zone_id  = var.cloudflare_zone_id
  name     = each.value.name
  type     = each.value.type
  priority = each.value.priority
  # Cloudflare stores TXT content between quotes, and reports it that way.
  content = each.value.type == "TXT" ? "\"${each.value.value}\"" : each.value.value
  proxied = false
  ttl     = 1
  comment = "Amazon SES (Terraform)"
}

# ---- the panel: a static site on Cloudflare Pages. The CI uploads each build (direct upload). ----
resource "cloudflare_pages_project" "panel" {
  account_id        = var.cloudflare_account_id
  name              = var.name
  production_branch = "main"
}

resource "cloudflare_dns_record" "panel" {
  zone_id = var.cloudflare_zone_id
  name    = var.domain
  type    = "CNAME"
  content = cloudflare_pages_project.panel.subdomain
  proxied = true
  ttl     = 1
  comment = "Sitewatch panel on Cloudflare Pages (Terraform)"
}

resource "cloudflare_pages_domain" "panel" {
  account_id   = var.cloudflare_account_id
  project_name = cloudflare_pages_project.panel.name
  name         = var.domain

  # Pages validates the domain by looking for the CNAME above.
  depends_on = [cloudflare_dns_record.panel]
}
