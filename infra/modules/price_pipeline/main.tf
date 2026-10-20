# The daily price pipeline: EventBridge Scheduler starts a Python Lambda that asks the app which cards to price,
# prices them from TCGdex, lands the raw responses in an S3 data lake and posts the prices back to the app over a
# signed HTTPS request. See docs/price-pipeline.md.

locals {
  function_name = "${var.name}-price-pipeline"
  package_dir   = "${var.source_dir}/price_ingest"
}

data "aws_caller_identity" "current" {}

# ---------------------------------------------------------------- data lake

resource "aws_s3_bucket" "lake" {
  bucket        = "${var.name}-price-lake-${var.bucket_suffix}"
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_public_access_block" "lake" {
  bucket                  = aws_s3_bucket.lake.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "lake" {
  bucket = aws_s3_bucket.lake.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "lake" {
  bucket = aws_s3_bucket.lake.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Raw files are written once; versioning keeps an overwritten or deleted object recoverable for 30 days
resource "aws_s3_bucket_versioning" "lake" {
  bucket = aws_s3_bucket.lake.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "lake" {
  bucket = aws_s3_bucket.lake.id
  rule {
    id     = "expire-run-logs"
    status = "Enabled"
    filter {
      prefix = "runs/"
    }
    expiration {
      days = 400
    }
  }
  rule {
    id     = "tidy-up"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
  depends_on = [aws_s3_bucket_versioning.lake]
}

data "aws_iam_policy_document" "lake_tls_only" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.lake.arn, "${aws_s3_bucket.lake.arn}/*"]
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

resource "aws_s3_bucket_policy" "lake" {
  bucket     = aws_s3_bucket.lake.id
  policy     = data.aws_iam_policy_document.lake_tls_only.json
  depends_on = [aws_s3_bucket_public_access_block.lake]
}

# ---------------------------------------------------------------- shared secret

# Signs the pipeline's requests to the app. Terraform only creates the container; the value (openssl rand -hex 32)
# is set with the AWS CLI and also given to the app as PIPELINE_SHARED_SECRET, so it never reaches Terraform state.
resource "aws_secretsmanager_secret" "shared" {
  name                    = "${var.name}/price-pipeline"
  description             = "HMAC key shared by the price pipeline and the ${var.name} app"
  recovery_window_in_days = var.recovery_window_days
}

# ---------------------------------------------------------------- function

data "archive_file" "package" {
  type        = "zip"
  output_path = "${path.module}/build/${local.function_name}.zip"

  dynamic "source" {
    for_each = fileset(local.package_dir, "*.py")
    content {
      content  = file("${local.package_dir}/${source.value}")
      filename = "price_ingest/${source.value}"
    }
  }
}

resource "aws_cloudwatch_log_group" "function" {
  # checkov:skip=CKV_AWS_338: each run's full record is kept in the bucket (runs/) for 400 days; these are debug logs
  name              = "/aws/lambda/${local.function_name}"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "function" {
  name               = local.function_name
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

data "aws_iam_policy_document" "function" {
  statement {
    sid       = "WriteOwnLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.function.arn}:*"]
  }
  statement {
    sid       = "WriteDataLake"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.lake.arn}/raw/*", "${aws_s3_bucket.lake.arn}/runs/*"]
  }
  statement {
    sid       = "Tracing"
    actions   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords"]
    resources = ["*"] # X-Ray has no resource-level permissions
  }
  statement {
    sid       = "ReadSharedSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.shared.arn]
  }
}

resource "aws_iam_role_policy" "function" {
  name   = "price-pipeline"
  role   = aws_iam_role.function.id
  policy = data.aws_iam_policy_document.function.json
}

resource "aws_lambda_function" "this" {
  # checkov:skip=CKV_AWS_117: it only calls public HTTPS APIs; inside the VPC it would need a NAT gateway (~$32/month)
  # checkov:skip=CKV_AWS_116: the schedule retries twice and failures alarm on Errors; runs are idempotent, so a rerun is the fix
  # checkov:skip=CKV_AWS_115: reserved concurrency fails on new accounts (10 concurrent executions); one daily schedule invokes it
  # checkov:skip=CKV_AWS_173: the environment holds no secrets (the shared key is read from Secrets Manager at run time)
  # checkov:skip=CKV_AWS_272: the package is built by Terraform from this reviewed repository, not uploaded by hand
  function_name    = local.function_name
  description      = "Daily TCGdex prices for ${var.app_base_url}"
  role             = aws_iam_role.function.arn
  runtime          = "python3.13"
  architectures    = ["arm64"]
  handler          = "price_ingest.handler.handler"
  filename         = data.archive_file.package.output_path
  source_code_hash = data.archive_file.package.output_base64sha256
  memory_size      = 256
  timeout          = 900

  environment {
    variables = {
      ENVIRONMENT             = var.environment
      APP_BASE_URL            = var.app_base_url
      SHARED_SECRET_ARN       = aws_secretsmanager_secret.shared.arn
      DATA_BUCKET             = aws_s3_bucket.lake.bucket
      MAX_REQUESTS_PER_SECOND = tostring(var.max_requests_per_second)
      MAX_ERROR_RATIO         = tostring(var.max_error_ratio)
    }
  }

  tracing_config {
    mode = "Active"
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.function.name
  }

  depends_on = [aws_iam_role_policy.function]
}

# ---------------------------------------------------------------- schedule

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${local.function_name}-schedule"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

data "aws_iam_policy_document" "invoke" {
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [aws_lambda_function.this.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "invoke-price-pipeline"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.invoke.json
}

resource "aws_scheduler_schedule" "daily" {
  name                         = local.function_name
  description                  = "Starts the daily price pipeline run"
  schedule_expression          = "cron(${var.schedule_cron})"
  schedule_expression_timezone = var.timezone
  flexible_time_window {
    mode = "OFF"
  }
  target {
    arn      = aws_lambda_function.this.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ trigger = "schedule" })
    # Runs are idempotent (the app skips prices it already has), so a retry is safe
    retry_policy {
      maximum_retry_attempts       = 2
      maximum_event_age_in_seconds = 3600
    }
  }
}

# ---------------------------------------------------------------- alarms

resource "aws_cloudwatch_metric_alarm" "errors" {
  alarm_name          = "${local.function_name}-errors"
  alarm_description   = "The price pipeline failed (see its run log in s3://${aws_s3_bucket.lake.bucket}/runs/)"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.this.function_name }
  statistic           = "Sum"
  period              = 3600
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [var.alerts_topic_arn]
  ok_actions          = [var.alerts_topic_arn]
}

# Catches what the errors alarm can't: the schedule not firing, or the function never being invoked
resource "aws_cloudwatch_metric_alarm" "stale" {
  alarm_name          = "${local.function_name}-no-success-26h"
  alarm_description   = "No successful price pipeline run in 26 hours"
  namespace           = "TailorCards/PricePipeline"
  metric_name         = "RunSucceeded"
  dimensions          = { Environment = var.environment }
  statistic           = "Sum"
  period              = 3600
  evaluation_periods  = 26
  datapoints_to_alarm = 26
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [var.alerts_topic_arn]
  ok_actions          = [var.alerts_topic_arn]
}
