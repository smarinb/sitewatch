variable "region" {
  description = "AWS region. SES and the instance live here."
  type        = string
  default     = "us-east-1"
}

variable "name" {
  description = "Name prefix and value of the Project tag."
  type        = string
  default     = "sitewatch"
}

variable "instance_type" {
  type    = string
  default = "t3.micro"
}

variable "root_volume_gb" {
  type    = number
  default = 20
}

variable "swap_gb" {
  description = "Swap file size. The t3.micro has 1 GB of RAM, so swap is what keeps the JVM and PostgreSQL alive."
  type        = number
  default     = 2
}

variable "ssh_allowed_cidr" {
  description = "The only address range allowed to SSH in, for example 203.0.113.7/32."
  type        = string

  validation {
    condition     = can(cidrhost(var.ssh_allowed_cidr, 0)) && var.ssh_allowed_cidr != "0.0.0.0/0" && var.ssh_allowed_cidr != "::/0"
    error_message = "Must be a valid CIDR and must not be open to the whole internet."
  }
}

variable "ssh_public_key_path" {
  description = "Public key installed on the instance for the ubuntu user."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "existing_eip_allocation_id" {
  description = <<-EOT
    Allocation id (eipalloc-...) of an Elastic IP you already own, to use instead of creating a new one.
    Terraform only associates it with the instance and never manages it, so `terraform destroy` leaves the
    address allocated and yours. If it is attached to another instance, that instance loses it.
    Leave empty to create a new one.
  EOT
  type        = string
  default     = null

  validation {
    condition     = var.existing_eip_allocation_id == null || can(regex("^eipalloc-[0-9a-f]+$", var.existing_eip_allocation_id))
    error_message = "Must look like eipalloc-0123456789abcdef0."
  }
}

variable "domain" {
  description = "Public hostname of the panel. It is also the SES sending domain and the allowed CORS origin."
  type        = string
  default     = "monitor.sergiomarin.dev"
}

variable "mail_from_subdomain" {
  description = "Label under the domain used as the custom MAIL FROM domain (makes SPF align with the sender)."
  type        = string
  default     = "mail"
}

variable "alert_email" {
  description = "Verified as an SES recipient (the account is in the SES sandbox) and notified by the budget. Set it in terraform.tfvars, not in the repo."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "Must be an email address."
  }
}

variable "monthly_budget_usd" {
  description = "Monthly cost limit for resources tagged Project=<name>. Alerts at 80 % actual and 100 % forecast."
  type        = number
  default     = 20
}

variable "cloudflare_zone_id" {
  description = "Id of the Cloudflare zone that holds the domain (Cloudflare dashboard, zone overview, right column)."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{32}$", var.cloudflare_zone_id))
    error_message = "Must be a 32-character hexadecimal id."
  }
}

variable "cloudflare_account_id" {
  description = "Id of the Cloudflare account, needed for Pages."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{32}$", var.cloudflare_account_id))
    error_message = "Must be a 32-character hexadecimal id."
  }
}

variable "github_repo" {
  description = "GitHub repository (owner/name) whose main branch may deploy. It assumes the deploy role through OIDC, with no stored keys."
  type        = string
  default     = "smarinb/sitewatch"
}

variable "enable_budget" {
  description = <<-EOT
    Create the cost allocation tag and the monthly budget. The tag only exists in Billing about 24 hours after
    the first tagged resources are created, so leave this false on the first apply and set it to true afterwards.
  EOT
  type        = bool
  default     = false
}
