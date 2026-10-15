# One small ARM (Graviton) instance running the Spring Boot jar as a systemd service behind Nginx, which terminates
# HTTPS with a Let's Encrypt certificate. No SSH: port 22 is closed and access is through SSM Session Manager.

data "aws_ssm_parameter" "al2023_arm" {
  count = var.ami_id == null ? 1 : 0
  name  = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

locals {
  ami_id = var.ami_id != null ? var.ami_id : nonsensitive(data.aws_ssm_parameter.al2023_arm[0].value)
}

# ---------------------------------------------------------------- network access

resource "aws_security_group" "app" {
  name        = "${var.name}-app"
  description = "HTTPS (and HTTP for redirects and certificate checks) to the app server; no SSH"
  vpc_id      = var.vpc_id
  tags        = { Name = "${var.name}-app" }
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.app.id
  description       = "HTTP: redirects to HTTPS, and Let's Encrypt domain validation"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "https_out" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS out: AWS APIs (SSM, Secrets Manager, S3, SES, CloudWatch), OS packages, Let's Encrypt, card price APIs"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "postgres_out" {
  security_group_id = aws_security_group.app.id
  description       = "PostgreSQL to the database inside the VPC"
  ip_protocol       = "tcp"
  from_port         = 5432
  to_port           = 5432
  cidr_ipv4         = var.vpc_cidr_block
}

# ---------------------------------------------------------------- permissions

data "aws_iam_policy_document" "assume_ec2" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${var.name}-app"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:${var.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:${var.partition}:iam::aws:policy/CloudWatchAgentServerPolicy"
}

data "aws_iam_policy_document" "app" {
  statement {
    sid       = "ReadOwnSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [var.db_secret_arn, var.app_secret_arn]
  }
  statement {
    sid       = "ReadDeployArtifacts"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.artifacts.arn}/*"]
  }
  statement {
    sid       = "StoreImages"
    actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
    resources = ["${var.images_bucket_arn}/buylist/*"]
  }
  statement {
    sid       = "SendEmailFromOwnDomain"
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = [var.ses_identity_arn]
    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.email_from]
    }
  }
}

resource "aws_iam_role_policy" "app" {
  name   = "app"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${var.name}-app"
  role = aws_iam_role.app.name
}

# ---------------------------------------------------------------- the server

resource "aws_instance" "app" {
  ami                    = local.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.public_subnet_id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name
  monitoring             = false # basic 5-minute metrics; detailed monitoring is ~$2/month extra
  ebs_optimized          = true

  metadata_options {
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 1
    http_endpoint               = "enabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    delete_on_termination = true
  }

  user_data = templatefile("${path.module}/templates/cloud-init.yaml.tftpl", {
    region                 = var.region
    api_domain             = var.api_domain
    frontend_url           = var.frontend_url
    images_bucket          = var.images_bucket_name
    images_public_url      = var.images_public_url
    email_from             = var.email_from
    db_secret_arn          = var.db_secret_arn
    app_secret_arn         = var.app_secret_arn
    letsencrypt_email      = var.letsencrypt_email
    log_group_app          = var.log_group_names.app
    log_group_nginx_access = var.log_group_names.nginx_access
    log_group_nginx_error  = var.log_group_names.nginx_error
  })

  tags = { Name = "${var.name}-app" }

  lifecycle {
    # New Amazon Linux images and edited boot scripts don't replace a running server: patch in place with dnf
    # (see the runbook), or taint the instance to rebuild it on purpose.
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id
  tags     = { Name = "${var.name}-app" }
}

resource "aws_route53_record" "api" {
  zone_id = var.zone_id
  name    = var.api_domain
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}

# ---------------------------------------------------------------- deploy artifacts (jars from CI)

resource "aws_s3_bucket" "artifacts" {
  bucket        = "${var.name}-artifacts-${var.bucket_suffix}"
  force_destroy = true # only build outputs live here
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  versioning_configuration {
    status = "Enabled" # a jar overwritten by mistake can be recovered
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    id     = "expire-old-builds"
    status = "Enabled"
    filter {}
    expiration {
      days = 30
    }
    noncurrent_version_expiration {
      noncurrent_days = 7
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

data "aws_iam_policy_document" "artifacts_tls_only" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.artifacts.arn, "${aws_s3_bucket.artifacts.arn}/*"]
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

resource "aws_s3_bucket_policy" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  policy = data.aws_iam_policy_document.artifacts_tls_only.json
}
