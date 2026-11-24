output "public_ip" {
  description = "Elastic IP of the instance. Point api.<domain> at it (the Cloudflare step)."
  value       = aws_eip.api.public_ip
}

output "instance_id" {
  value = aws_instance.api.id
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_eip.api.public_ip}"
}

output "session_manager_command" {
  description = "A shell without SSH, if your IP changed."
  value       = "aws ssm start-session --target ${aws_instance.api.id}"
}

# The DNS records SES needs, ready for the Cloudflare step.
output "ses_dns_records" {
  description = "Records to create in DNS so SES can verify the domain and sign mail with DKIM."
  value = concat(
    [
      for token in aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens : {
        type     = "CNAME"
        name     = "${token}._domainkey.${var.domain}"
        value    = "${token}.dkim.amazonses.com"
        priority = null
      }
    ],
    [
      {
        type     = "MX"
        name     = "${var.mail_from_subdomain}.${var.domain}"
        value    = "feedback-smtp.${var.region}.amazonses.com"
        priority = 10
      },
      {
        type     = "TXT"
        name     = "${var.mail_from_subdomain}.${var.domain}"
        value    = "v=spf1 include:amazonses.com ~all"
        priority = null
      },
      {
        type     = "TXT"
        name     = "_dmarc.${var.domain}"
        value    = "v=DMARC1; p=none;"
        priority = null
      },
    ],
  )
}

output "ssm_prefix" {
  value = local.ssm_prefix
}
