# The certificate the origin presents to Cloudflare. Only Cloudflare trusts it, which is all that
# "Full (strict)" needs: Cloudflare checks it, visitors never see it. Valid 15 years, so nothing to renew.
resource "tls_private_key" "origin" {
  algorithm   = "ECDSA"
  ecdsa_curve = "P256"
}

resource "tls_cert_request" "origin" {
  private_key_pem = tls_private_key.origin.private_key_pem

  subject {
    common_name = local.api_hostname
  }

  dns_names = [local.api_hostname]
}

resource "cloudflare_origin_ca_certificate" "origin" {
  csr                = tls_cert_request.origin.cert_request_pem
  hostnames          = [local.api_hostname]
  request_type       = "origin-ecc"
  requested_validity = 5475
}

# deploy.sh copies these two parameters to the server before starting the proxy.
resource "aws_ssm_parameter" "origin_cert" {
  name  = "${local.ssm_tls_prefix}origin-cert"
  type  = "SecureString"
  value = cloudflare_origin_ca_certificate.origin.certificate
}

resource "aws_ssm_parameter" "origin_key" {
  name  = "${local.ssm_tls_prefix}origin-key"
  type  = "SecureString"
  value = tls_private_key.origin.private_key_pem
}

# "Full (strict)" for the API only. The zone's own mode stays as it is: the portfolio depends on it.
resource "cloudflare_ruleset" "api_tls" {
  zone_id = var.cloudflare_zone_id
  name    = "Sitewatch API: TLS mode"
  kind    = "zone"
  phase   = "http_config_settings"

  rules = [{
    action      = "set_config"
    description = "Verify the origin certificate for the Sitewatch API"
    expression  = "(http.host eq \"${local.api_hostname}\")"
    enabled     = true
    action_parameters = {
      ssl = "strict"
    }
  }]

  depends_on = [aws_ssm_parameter.origin_cert, aws_ssm_parameter.origin_key]
}
