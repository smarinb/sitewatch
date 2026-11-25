# Two private buckets: nightly PostgreSQL dumps, and the Terraform state (moved out of this laptop).
# Both are encrypted, versioned and closed to the public. The names carry the account id because bucket
# names are global.
locals {
  backup_bucket = "${var.name}-backups-${data.aws_caller_identity.current.account_id}"
  state_bucket  = "${var.name}-tfstate-${data.aws_caller_identity.current.account_id}"
  buckets = {
    backups = local.backup_bucket
    tfstate = local.state_bucket
  }
}

resource "aws_s3_bucket" "this" {
  for_each = local.buckets

  bucket = each.value
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each = aws_s3_bucket.this

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id
  versioning_configuration {
    status = "Enabled"
  }
}

data "aws_iam_policy_document" "tls_only" {
  for_each = aws_s3_bucket.this

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [each.value.arn, "${each.value.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "tls_only" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id
  policy = data.aws_iam_policy_document.tls_only[each.key].json

  depends_on = [aws_s3_bucket_public_access_block.this]
}

# Dumps are kept 30 days; older versions of the state file 90 days. Nothing else is ever deleted.
resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.this["backups"].id

  rule {
    id     = "expire-old-dumps"
    status = "Enabled"
    filter {
      prefix = "postgres/"
    }
    expiration {
      days = var.backup_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = var.backup_retention_days
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 2
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.this["tfstate"].id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

# The server may only add dumps. It cannot read or delete them, so a compromised host cannot touch past backups.
data "aws_iam_policy_document" "write_backups" {
  statement {
    sid       = "WriteDumpsOnly"
    actions   = ["s3:PutObject", "s3:AbortMultipartUpload"]
    resources = ["${aws_s3_bucket.this["backups"].arn}/postgres/*"]
  }
}

resource "aws_iam_role_policy" "write_backups" {
  name   = "write-postgres-dumps"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.write_backups.json
}
