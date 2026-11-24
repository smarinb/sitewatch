# Email alerts go out through Amazon SES, authenticated with DKIM on a domain we control.
# New accounts start in the SES sandbox: mail can only go to verified addresses and at most 200 a day.

resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }
}

# Bounces and SPF are handled on a subdomain, so the envelope sender aligns with the From domain.
resource "aws_sesv2_email_identity_mail_from_attributes" "domain" {
  email_identity         = aws_sesv2_email_identity.domain.email_identity
  mail_from_domain       = "${var.mail_from_subdomain}.${var.domain}"
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"
}

# In the sandbox the recipient must be verified too. AWS emails this address a confirmation link on apply.
resource "aws_sesv2_email_identity" "recipient" {
  email_identity = var.alert_email
}

# ---- SMTP credentials: an IAM user that can do nothing but send from our own address ----
resource "aws_iam_user" "smtp" {
  name = "${var.name}-ses-smtp"
}

data "aws_iam_policy_document" "smtp_send" {
  statement {
    actions   = ["ses:SendRawEmail"]
    resources = ["arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/*"]

    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = ["alerts@${var.domain}"]
    }
  }
}

resource "aws_iam_user_policy" "smtp_send" {
  name   = "send-alerts-only"
  user   = aws_iam_user.smtp.name
  policy = data.aws_iam_policy_document.smtp_send.json
}

resource "aws_iam_access_key" "smtp" {
  user = aws_iam_user.smtp.name
}

# The records that make SES trust the domain. Stable keys, because the DKIM tokens are only known after SES creates them.
locals {
  ses_dns_records = merge(
    {
      for i in range(3) : "dkim-${i}" => {
        type     = "CNAME"
        name     = "${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[i]}._domainkey.${var.domain}"
        value    = "${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[i]}.dkim.amazonses.com"
        priority = null
      }
    },
    {
      mail-from-mx = {
        type     = "MX"
        name     = "${var.mail_from_subdomain}.${var.domain}"
        value    = "feedback-smtp.${var.region}.amazonses.com"
        priority = 10
      }
      mail-from-spf = {
        type     = "TXT"
        name     = "${var.mail_from_subdomain}.${var.domain}"
        value    = "v=spf1 include:amazonses.com ~all"
        priority = null
      }
      dmarc = {
        type     = "TXT"
        name     = "_dmarc.${var.domain}"
        value    = "v=DMARC1; p=none;"
        priority = null
      }
    },
  )
}
