terraform {
  required_version = ">= 1.9"

  required_providers {
    aws        = { source = "hashicorp/aws", version = "~> 6.0" }
    cloudflare = { source = "cloudflare/cloudflare", version = "~> 5.0" }
    http       = { source = "hashicorp/http", version = "~> 3.4" }
    random     = { source = "hashicorp/random", version = "~> 3.6" }
  }
}

provider "aws" {
  region = var.region

  # Every resource carries these tags; "Project" is also the cost allocation tag the budget filters on.
  default_tags {
    tags = {
      Project   = var.name
      ManagedBy = "terraform"
    }
  }
}

# Reads CLOUDFLARE_API_TOKEN from the environment; the token is never written in the repo.
provider "cloudflare" {}
