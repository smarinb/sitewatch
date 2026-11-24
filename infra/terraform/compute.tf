# Latest Ubuntu 24.04 LTS, resolved from Canonical's public SSM parameter.
data "aws_ssm_parameter" "ubuntu_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

resource "aws_key_pair" "admin" {
  key_name   = "${var.name}-admin"
  public_key = file(pathexpand(var.ssh_public_key_path))
}

# ---- instance role: read its own secrets, and allow Session Manager as an SSH fallback ----
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${var.name}-instance"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "session_manager" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    sid       = "ReadOwnParameters"
    actions   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}*"]
  }
}

resource "aws_iam_role_policy" "read_secrets" {
  name   = "read-own-parameters"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

resource "aws_iam_instance_profile" "instance" {
  name = "${var.name}-instance"
  role = aws_iam_role.instance.name
}

# ---- the instance ----
resource "aws_instance" "api" {
  ami                    = data.aws_ssm_parameter.ubuntu_ami.value
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.api.id]
  key_name               = aws_key_pair.admin.key_name
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  # t3 defaults to "unlimited" credits, which bills for CPU bursts beyond the baseline.
  # "standard" caps the instance at its baseline instead of the bill growing.
  credit_specification {
    cpu_credits = "standard"
  }

  # IMDSv2 only, and one hop: containers cannot reach the instance's credentials.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  user_data = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    region      = var.region
    swap_bytes  = var.swap_gb * 1073741824
    ssm_prefix  = local.ssm_prefix
    smtp_host   = "email-smtp.${var.region}.amazonaws.com"
    mail_from   = "Sitewatch <alerts@${var.domain}>"
    cors_origin = "https://${var.domain}"
  })

  # Secrets must exist before first boot, because cloud-init fetches them.
  depends_on = [aws_ssm_parameter.managed, aws_internet_gateway.main]

  lifecycle {
    # A newer Ubuntu image must not replace a running server.
    ignore_changes = [ami]
  }

  tags = { Name = var.name }
}

# Either a new Elastic IP, or one you already own (see existing_eip_allocation_id).
resource "aws_eip" "api" {
  count = var.existing_eip_allocation_id == null ? 1 : 0

  domain = "vpc"

  tags = { Name = var.name }

  depends_on = [aws_internet_gateway.main]
}

data "aws_eip" "existing" {
  count = var.existing_eip_allocation_id == null ? 0 : 1

  id = var.existing_eip_allocation_id
}

locals {
  eip_allocation_id = var.existing_eip_allocation_id != null ? var.existing_eip_allocation_id : aws_eip.api[0].id
  public_ip         = var.existing_eip_allocation_id != null ? data.aws_eip.existing[0].public_ip : aws_eip.api[0].public_ip
}

resource "aws_eip_association" "api" {
  instance_id   = aws_instance.api.id
  allocation_id = local.eip_allocation_id

  # Takes the address from whatever instance has it now.
  allow_reassociation = true

  depends_on = [aws_internet_gateway.main]
}
