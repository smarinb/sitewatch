# Secrets live in SSM Parameter Store (SecureString, free tier), never in the repo or in an image.
# The instance reads everything under this prefix at boot; the parameter name is the environment variable.
locals {
  ssm_prefix     = "/${var.name}/env/"
  ssm_tls_prefix = "/${var.name}/tls/"

  secrets = {
    JWT_SECRET           = random_id.jwt_secret.b64_std
    DB_PASSWORD          = random_password.db.result
    SPRING_MAIL_USERNAME = aws_iam_access_key.smtp.id
    SPRING_MAIL_PASSWORD = aws_iam_access_key.smtp.ses_smtp_password_v4
  }
}

# 32 random bytes, Base64 encoded: what the API requires of JWT_SECRET.
resource "random_id" "jwt_secret" {
  byte_length = 32
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_ssm_parameter" "managed" {
  for_each = toset(["JWT_SECRET", "DB_PASSWORD", "SPRING_MAIL_USERNAME", "SPRING_MAIL_PASSWORD"])

  name  = "${local.ssm_prefix}${each.key}"
  type  = "SecureString"
  value = local.secrets[each.key]
}

# TELEGRAM_BOT_TOKEN is deliberately not managed here: it is yours, and keeping it out of Terraform keeps it
# out of the state file. Set it once with:
#   aws ssm put-parameter --name /sitewatch/env/TELEGRAM_BOT_TOKEN --type SecureString --value '<token>'
# and run `sudo sitewatch-fetch-secrets` on the instance.
