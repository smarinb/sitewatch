# Cloudflare publishes the ranges its proxy connects from. Only those may reach 80/443, so the
# origin cannot be hit directly. The ranges change rarely; a later plan shows any drift.
data "http" "cloudflare_ipv4" {
  url = "https://www.cloudflare.com/ips-v4"

  lifecycle {
    postcondition {
      condition     = self.status_code == 200
      error_message = "Could not download Cloudflare's IPv4 ranges."
    }
  }
}

data "http" "cloudflare_ipv6" {
  url = "https://www.cloudflare.com/ips-v6"

  lifecycle {
    postcondition {
      condition     = self.status_code == 200
      error_message = "Could not download Cloudflare's IPv6 ranges."
    }
  }
}

locals {
  cloudflare_ipv4 = toset(compact(split("\n", trimspace(data.http.cloudflare_ipv4.response_body))))
  cloudflare_ipv6 = toset(compact(split("\n", trimspace(data.http.cloudflare_ipv6.response_body))))

  web_rules = merge(
    { for pair in setproduct(local.cloudflare_ipv4, [80, 443]) : "v4-${pair[0]}-${pair[1]}" => { cidr = pair[0], port = pair[1], v6 = false } },
    { for pair in setproduct(local.cloudflare_ipv6, [80, 443]) : "v6-${pair[0]}-${pair[1]}" => { cidr = pair[0], port = pair[1], v6 = true } },
  )
}

resource "aws_security_group" "api" {
  name        = "${var.name}-api"
  description = "Sitewatch API: web only from Cloudflare, SSH only from one address"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-api" }
}

resource "aws_vpc_security_group_ingress_rule" "web" {
  for_each = local.web_rules

  security_group_id = aws_security_group.api.id
  description       = "Cloudflare to port ${each.value.port}"
  ip_protocol       = "tcp"
  from_port         = each.value.port
  to_port           = each.value.port
  cidr_ipv4         = each.value.v6 ? null : each.value.cidr
  cidr_ipv6         = each.value.v6 ? each.value.cidr : null
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.api.id
  description       = "SSH from the admin address"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.ssh_allowed_cidr
}

# The instance must reach apt, Docker Hub / GHCR, SSM and SES.
resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.api.id
  description       = "All outbound traffic"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
