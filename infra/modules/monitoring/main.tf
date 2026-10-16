# Logs, alarms and the monthly budget alert. Alarms and budget notifications go to one email address (confirm the
# SNS subscription email after the first apply).

resource "aws_cloudwatch_log_group" "this" {
  for_each          = toset(["app", "nginx-access", "nginx-error", "vpc-flow-rejected"])
  name              = "/${var.name}/${each.key}"
  retention_in_days = var.log_retention_days
}

# ---------------------------------------------------------------- VPC flow logs role

data "aws_iam_policy_document" "flow_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "flow_logs" {
  name               = "${var.name}-flow-logs"
  assume_role_policy = data.aws_iam_policy_document.flow_assume.json
}

data "aws_iam_policy_document" "flow_write" {
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
    resources = ["${aws_cloudwatch_log_group.this["vpc-flow-rejected"].arn}:*"]
  }
}

resource "aws_iam_role_policy" "flow_logs" {
  name   = "write"
  role   = aws_iam_role.flow_logs.id
  policy = data.aws_iam_policy_document.flow_write.json
}

# ---------------------------------------------------------------- alerts

resource "aws_sns_topic" "alerts" {
  name              = "${var.name}-alerts"
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

locals {
  alarms = {
    app-cpu-high = {
      description = "App server CPU above 80% for 10 minutes"
      namespace   = "AWS/EC2", metric = "CPUUtilization", stat = "Average", threshold = 80, comparison = "GreaterThanThreshold"
      period      = 300, periods = 2, dimensions = { InstanceId = var.instance_id }
    }
    app-status-check = {
      description = "App server failed an EC2 status check (instance or host problem)"
      namespace   = "AWS/EC2", metric = "StatusCheckFailed", stat = "Maximum", threshold = 1, comparison = "GreaterThanOrEqualToThreshold"
      period      = 60, periods = 3, dimensions = { InstanceId = var.instance_id }
    }
    app-memory-high = {
      description = "App server memory above 90% (CloudWatch agent)"
      namespace   = "CWAgent", metric = "mem_used_percent", stat = "Average", threshold = 90, comparison = "GreaterThanThreshold"
      period      = 300, periods = 2, dimensions = { InstanceId = var.instance_id }
    }
    db-cpu-high = {
      description = "Database CPU above 80% for 15 minutes"
      namespace   = "AWS/RDS", metric = "CPUUtilization", stat = "Average", threshold = 80, comparison = "GreaterThanThreshold"
      period      = 300, periods = 3, dimensions = { DBInstanceIdentifier = var.db_instance_id }
    }
    db-storage-low = {
      description = "Database free storage below 2 GB"
      namespace   = "AWS/RDS", metric = "FreeStorageSpace", stat = "Minimum", threshold = 2147483648, comparison = "LessThanThreshold"
      period      = 300, periods = 1, dimensions = { DBInstanceIdentifier = var.db_instance_id }
    }
    app-errors = {
      description = "More than 10 ERROR lines in the app log within 5 minutes"
      namespace   = "TailorCards/${var.name}", metric = "AppErrors", stat = "Sum", threshold = 10, comparison = "GreaterThanThreshold"
      period      = 300, periods = 1, dimensions = {}
    }
    nginx-5xx = {
      description = "More than 10 server errors (HTTP 5xx) within 5 minutes"
      namespace   = "TailorCards/${var.name}", metric = "Http5xx", stat = "Sum", threshold = 10, comparison = "GreaterThanThreshold"
      period      = 300, periods = 1, dimensions = {}
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "app_errors" {
  name           = "${var.name}-app-errors"
  log_group_name = aws_cloudwatch_log_group.this["app"].name
  pattern        = "ERROR"
  metric_transformation {
    name          = "AppErrors"
    namespace     = "TailorCards/${var.name}"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_log_metric_filter" "http_5xx" {
  name           = "${var.name}-http-5xx"
  log_group_name = aws_cloudwatch_log_group.this["nginx-access"].name
  # Nginx combined log format: the status code is the 9th field
  pattern = "[ip, ident, user, timestamp, request, status_code = 5*, ...]"
  metric_transformation {
    name          = "Http5xx"
    namespace     = "TailorCards/${var.name}"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each            = local.alarms
  alarm_name          = "${var.name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  statistic           = each.value.stat
  threshold           = each.value.threshold
  comparison_operator = each.value.comparison
  period              = each.value.period
  evaluation_periods  = each.value.periods
  dimensions          = each.value.dimensions
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# ---------------------------------------------------------------- monthly budget

resource "aws_budgets_budget" "monthly" {
  name         = "${var.name}-monthly"
  budget_type  = "COST"
  limit_amount = var.monthly_budget_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # Count usage before credits: with promotional credits applied, spend shows $0 and the alerts never fire
  cost_types {
    include_credit = false
    include_refund = false
  }

  dynamic "cost_filter" {
    for_each = var.budget_tag == null ? [] : [var.budget_tag]
    content {
      name   = "TagKeyValue"
      values = [format("user:%s$%s", cost_filter.value.key, cost_filter.value.value)]
    }
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}
