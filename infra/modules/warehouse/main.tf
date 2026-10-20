# The warehouse: Glue Data Catalog tables over the data lake, an Athena workgroup, and a nightly Lambda that copies
# the app's datasets into the lake, (re)creates the SQL views and runs the data-quality checks. See docs/warehouse.md.

locals {
  function_name = "${var.name}-warehouse"
  database      = replace("${var.name}_warehouse", "-", "_")
  schema        = jsondecode(file("${var.source_dir}/warehouse/schema.json")).tables

  # The Lambda package: both Python packages, the raw-table schema and the view definitions
  package_files = merge(
    { for f in fileset(var.source_dir, "price_ingest/*.py") : f => f },
    { for f in fileset(var.source_dir, "warehouse/*.py") : f => f },
    { for f in fileset(var.source_dir, "warehouse/sql/*.sql") : f => f },
    { "warehouse/schema.json" = "warehouse/schema.json" },
  )
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ---------------------------------------------------------------- catalog

resource "aws_glue_catalog_database" "this" {
  name        = local.database
  description = "Tailor Cards ${var.environment} warehouse: raw exports, star schema views, marts and data-quality checks"
}

# One table per exported dataset, defined by pipeline/warehouse/schema.json (the tests read the same file)
resource "aws_glue_catalog_table" "raw" {
  for_each      = local.schema
  name          = each.key
  database_name = aws_glue_catalog_database.this.name
  description   = "The app's ${each.value.dataset}, copied nightly"
  table_type    = "EXTERNAL_TABLE"
  parameters = {
    classification       = "json"
    compressionType      = "gzip"
    EXTERNAL             = "TRUE"
    "has_encrypted_data" = "false"
  }

  storage_descriptor {
    location      = "s3://${var.bucket}/warehouse/raw/${each.key}/"
    input_format  = "org.apache.hadoop.mapred.TextInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat"

    ser_de_info {
      serialization_library = "org.openx.data.jsonserde.JsonSerDe"
      parameters            = { "ignore.malformed.json" = "false" }
    }

    dynamic "columns" {
      for_each = each.value.columns
      content {
        name = columns.value[0]
        type = columns.value[1]
      }
    }
  }
}

# Every raw TCGdex response the price pipeline lands, for ad hoc questions. Partition projection finds the day
# folders without a crawler.
resource "aws_glue_catalog_table" "tcgdex_responses" {
  name          = "raw_tcgdex_responses"
  database_name = aws_glue_catalog_database.this.name
  description   = "One row per card per price pipeline run, with the response as received"
  table_type    = "EXTERNAL_TABLE"
  parameters = {
    classification                = "json"
    compressionType               = "gzip"
    EXTERNAL                      = "TRUE"
    "projection.enabled"          = "true"
    "projection.dt.type"          = "date"
    "projection.dt.format"        = "yyyy-MM-dd"
    "projection.dt.range"         = "2026-01-01,NOW"
    "projection.dt.interval"      = "1"
    "projection.dt.interval.unit" = "DAYS"
    "storage.location.template"   = "s3://${var.bucket}/raw/tcgdex/dt=$${dt}/"
  }

  partition_keys {
    name = "dt"
    type = "string"
  }

  storage_descriptor {
    location      = "s3://${var.bucket}/raw/tcgdex/"
    input_format  = "org.apache.hadoop.mapred.TextInputFormat"
    output_format = "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat"

    ser_de_info {
      serialization_library = "org.openx.data.jsonserde.JsonSerDe"
    }

    dynamic "columns" {
      for_each = [["run_id", "string"], ["card_id", "string"], ["status", "string"], ["fetched_at", "string"],
      ["price_usd", "decimal(10,2)"], ["variant", "string"], ["error", "string"], ["response", "string"]]
      content {
        name = columns.value[0]
        type = columns.value[1]
      }
    }
  }
}

# ---------------------------------------------------------------- Athena

resource "aws_athena_workgroup" "this" {
  name          = local.function_name
  description   = "Queries over the ${var.environment} warehouse"
  force_destroy = true

  configuration {
    enforce_workgroup_configuration    = true
    publish_cloudwatch_metrics_enabled = true
    bytes_scanned_cutoff_per_query     = var.bytes_scanned_cutoff

    result_configuration {
      output_location = "s3://${var.bucket}/athena-results/"
      encryption_configuration {
        encryption_option = "SSE_S3"
      }
    }
  }
}

# ---------------------------------------------------------------- function

data "archive_file" "package" {
  type        = "zip"
  output_path = "${path.module}/build/${local.function_name}.zip"

  dynamic "source" {
    for_each = local.package_files
    content {
      content  = file("${var.source_dir}/${source.value}")
      filename = source.key
    }
  }
}

resource "aws_cloudwatch_log_group" "function" {
  # checkov:skip=CKV_AWS_338: each run's full record is kept in the bucket (runs/warehouse/); these are debug logs
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

locals {
  catalog_arn  = "arn:aws:glue:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:catalog"
  database_arn = "arn:aws:glue:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:database/${local.database}"
  tables_arn   = "arn:aws:glue:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:table/${local.database}/*"
}

data "aws_iam_policy_document" "function" {
  statement {
    sid       = "WriteOwnLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.function.arn}:*"]
  }
  statement {
    sid       = "Tracing"
    actions   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords"]
    resources = ["*"] # X-Ray has no resource-level permissions
  }
  statement {
    sid       = "ReadSharedSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [var.shared_secret_arn]
  }
  statement {
    sid     = "WriteExportsAndRunLogs"
    actions = ["s3:PutObject"]
    resources = [
      "${var.bucket_arn}/warehouse/*",
      "${var.bucket_arn}/runs/warehouse/*",
      "${var.bucket_arn}/athena-results/*",
      "${var.bucket_arn}/dashboard/*",
    ]
  }
  # Athena reads source data and writes results with the caller's permissions
  statement {
    sid       = "AthenaReadsTheLake"
    actions   = ["s3:GetObject"]
    resources = ["${var.bucket_arn}/warehouse/raw/*", "${var.bucket_arn}/raw/tcgdex/*", "${var.bucket_arn}/athena-results/*"]
  }
  statement {
    sid       = "AthenaListsTheLake"
    actions   = ["s3:ListBucket", "s3:GetBucketLocation"]
    resources = [var.bucket_arn]
  }
  statement {
    sid = "RunQueries"
    actions = ["athena:StartQueryExecution", "athena:GetQueryExecution", "athena:GetQueryResults",
    "athena:StopQueryExecution", "athena:GetWorkGroup"]
    resources = [aws_athena_workgroup.this.arn]
  }
  # Views are Glue tables, so CREATE OR REPLACE VIEW creates and updates tables in this database only
  statement {
    sid = "ManageViews"
    actions = ["glue:GetDatabase", "glue:GetTable", "glue:GetTables", "glue:CreateTable", "glue:UpdateTable",
    "glue:GetPartitions"]
    resources = [local.catalog_arn, local.database_arn, local.tables_arn]
  }
}

resource "aws_iam_role_policy" "function" {
  name   = "warehouse"
  role   = aws_iam_role.function.id
  policy = data.aws_iam_policy_document.function.json
}

resource "aws_lambda_function" "this" {
  # checkov:skip=CKV_AWS_117: it calls the app's public API and Athena; inside the VPC it would need a NAT gateway (~$32/month)
  # checkov:skip=CKV_AWS_116: the schedule retries twice and failures alarm on Errors; a rerun replaces the same files
  # checkov:skip=CKV_AWS_115: reserved concurrency fails on new accounts (10 concurrent executions); one daily schedule invokes it
  # checkov:skip=CKV_AWS_173: the environment holds no secrets (the shared key is read from Secrets Manager at run time)
  # checkov:skip=CKV_AWS_272: the package is built by Terraform from this reviewed repository, not uploaded by hand
  function_name    = local.function_name
  description      = "Nightly warehouse refresh from ${var.app_base_url}"
  role             = aws_iam_role.function.arn
  runtime          = "python3.13"
  architectures    = ["arm64"]
  handler          = "warehouse.handler.handler"
  filename         = data.archive_file.package.output_path
  source_code_hash = data.archive_file.package.output_base64sha256
  memory_size      = 256
  timeout          = 600

  environment {
    variables = {
      ENVIRONMENT         = var.environment
      APP_BASE_URL        = var.app_base_url
      APP_TIMEOUT_SECONDS = tostring(var.app_timeout_seconds)
      SHARED_SECRET_ARN   = var.shared_secret_arn
      DATA_BUCKET         = var.bucket
      ATHENA_WORKGROUP    = aws_athena_workgroup.this.name
      GLUE_DATABASE       = aws_glue_catalog_database.this.name
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
  name   = "invoke-warehouse"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.invoke.json
}

resource "aws_scheduler_schedule" "nightly" {
  name                         = local.function_name
  description                  = "Starts the nightly warehouse refresh"
  schedule_expression          = "cron(${var.schedule_cron})"
  schedule_expression_timezone = var.timezone
  flexible_time_window {
    mode = "OFF"
  }
  target {
    arn      = aws_lambda_function.this.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ trigger = "schedule" })
    retry_policy {
      maximum_retry_attempts       = 2
      maximum_event_age_in_seconds = 3600
    }
  }
}

# ---------------------------------------------------------------- alarms

locals {
  alarms = {
    errors = {
      description = "The warehouse refresh failed (see s3://${var.bucket}/runs/warehouse/)"
      namespace   = "AWS/Lambda", metric = "Errors", dimensions = { FunctionName = local.function_name }
      comparison  = "GreaterThanOrEqualToThreshold", periods = 1, missing = "notBreaching"
    }
    no-success-26h = {
      description = "No successful warehouse refresh in 26 hours"
      namespace   = "TailorCards/Warehouse", metric = "RunSucceeded", dimensions = { Environment = var.environment }
      comparison  = "LessThanThreshold", periods = 26, missing = "breaching"
    }
    data-quality = {
      description = "A data-quality check found an error (query dq_results in Athena, or read the run log)"
      namespace   = "TailorCards/Warehouse", metric = "DataQualityErrors", dimensions = { Environment = var.environment }
      comparison  = "GreaterThanOrEqualToThreshold", periods = 1, missing = "notBreaching"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each            = local.alarms
  alarm_name          = "${local.function_name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  dimensions          = each.value.dimensions
  statistic           = "Sum"
  period              = 3600
  evaluation_periods  = each.value.periods
  datapoints_to_alarm = each.value.periods
  threshold           = 1
  comparison_operator = each.value.comparison
  treat_missing_data  = each.value.missing
  alarm_actions       = [var.alerts_topic_arn]
  ok_actions          = [var.alerts_topic_arn]
}

# ---------------------------------------------------------------- Monday email

locals {
  report_enabled = length(var.report_recipients) > 0
  report_name    = "${var.name}-weekly-report"
}

resource "aws_cloudwatch_log_group" "report" {
  # checkov:skip=CKV_AWS_338: each report is kept in the bucket (reports/weekly/); these are debug logs
  count             = local.report_enabled ? 1 : 0
  name              = "/aws/lambda/${local.report_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role" "report" {
  count              = local.report_enabled ? 1 : 0
  name               = local.report_name
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

data "aws_iam_policy_document" "report" {
  count = local.report_enabled ? 1 : 0
  statement {
    sid       = "WriteOwnLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.report[0].arn}:*"]
  }
  statement {
    sid       = "Tracing"
    actions   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords"]
    resources = ["*"] # X-Ray has no resource-level permissions
  }
  statement {
    sid       = "ReadTheWarehouse"
    actions   = ["s3:GetObject"]
    resources = ["${var.bucket_arn}/warehouse/raw/*", "${var.bucket_arn}/athena-results/*"]
  }
  statement {
    sid       = "ListTheLake"
    actions   = ["s3:ListBucket", "s3:GetBucketLocation"]
    resources = [var.bucket_arn]
  }
  statement {
    sid       = "WriteResultsAndReports"
    actions   = ["s3:PutObject"]
    resources = ["${var.bucket_arn}/athena-results/*", "${var.bucket_arn}/reports/weekly/*"]
  }
  statement {
    sid = "RunQueries"
    actions = ["athena:StartQueryExecution", "athena:GetQueryExecution", "athena:GetQueryResults",
    "athena:StopQueryExecution", "athena:GetWorkGroup"]
    resources = [aws_athena_workgroup.this.arn]
  }
  statement {
    sid       = "ReadViews"
    actions   = ["glue:GetDatabase", "glue:GetTable", "glue:GetTables", "glue:GetPartitions"]
    resources = [local.catalog_arn, local.database_arn, local.tables_arn]
  }
  # SES authorises a send against the sender's identity and, while the account is in the SES sandbox, the
  # recipient's verified identity too; the condition still pins the sender to the store's address
  statement {
    sid     = "SendFromTheStoreAddress"
    actions = ["ses:SendEmail"]
    resources = [
      var.ses_identity_arn,
      "arn:aws:ses:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:identity/*",
    ]
    condition {
      test     = "StringEquals"
      variable = "ses:FromAddress"
      values   = [var.report_from]
    }
  }
}

resource "aws_iam_role_policy" "report" {
  count  = local.report_enabled ? 1 : 0
  name   = "weekly-report"
  role   = aws_iam_role.report[0].id
  policy = data.aws_iam_policy_document.report[0].json
}

resource "aws_lambda_function" "report" {
  # checkov:skip=CKV_AWS_117: it calls Athena and SES over public endpoints; inside the VPC it would need a NAT gateway (~$32/month)
  # checkov:skip=CKV_AWS_116: the schedule retries twice and failures alarm on Errors
  # checkov:skip=CKV_AWS_115: reserved concurrency fails on new accounts (10 concurrent executions); one weekly schedule invokes it
  # checkov:skip=CKV_AWS_173: the environment holds no secrets (addresses and resource names only)
  # checkov:skip=CKV_AWS_272: the package is built by Terraform from this reviewed repository, not uploaded by hand
  count            = local.report_enabled ? 1 : 0
  function_name    = local.report_name
  description      = "Monday email of last week's results"
  role             = aws_iam_role.report[0].arn
  runtime          = "python3.13"
  architectures    = ["arm64"]
  handler          = "warehouse.report_handler.handler"
  filename         = data.archive_file.package.output_path
  source_code_hash = data.archive_file.package.output_base64sha256
  memory_size      = 256
  timeout          = 300

  environment {
    variables = {
      DATA_BUCKET       = var.bucket
      ATHENA_WORKGROUP  = aws_athena_workgroup.this.name
      GLUE_DATABASE     = aws_glue_catalog_database.this.name
      REPORT_FROM       = var.report_from
      REPORT_RECIPIENTS = join(",", var.report_recipients)
    }
  }

  tracing_config {
    mode = "Active"
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.report[0].name
  }

  depends_on = [aws_iam_role_policy.report]
}

data "aws_iam_policy_document" "invoke_report" {
  count = local.report_enabled ? 1 : 0
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [aws_lambda_function.report[0].arn]
  }
}

resource "aws_iam_role_policy" "scheduler_report" {
  count  = local.report_enabled ? 1 : 0
  name   = "invoke-weekly-report"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.invoke_report[0].json
}

resource "aws_scheduler_schedule" "report" {
  count                        = local.report_enabled ? 1 : 0
  name                         = local.report_name
  description                  = "Sends the Monday email"
  schedule_expression          = "cron(${var.report_cron})"
  schedule_expression_timezone = var.timezone
  flexible_time_window {
    mode = "OFF"
  }
  target {
    arn      = aws_lambda_function.report[0].arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ trigger = "schedule" })
    # One retry only: a retry after a partial failure could send the email twice
    retry_policy {
      maximum_retry_attempts       = 1
      maximum_event_age_in_seconds = 3600
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "report_errors" {
  count               = local.report_enabled ? 1 : 0
  alarm_name          = "${local.report_name}-errors"
  alarm_description   = "The Monday email failed (an unverified recipient while SES is in its sandbox is the usual cause)"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = local.report_name }
  statistic           = "Sum"
  period              = 3600
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [var.alerts_topic_arn]
  ok_actions          = [var.alerts_topic_arn]
}
