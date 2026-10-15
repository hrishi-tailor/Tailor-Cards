# Optional: stop the app server and database outside working hours to cut their running cost by about 60%.
# Storage, the public IP and everything else are still billed while stopped. RDS restarts itself after 7 days
# stopped; the Monday start schedule takes care of that.

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${var.name}-scheduler"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

data "aws_iam_policy_document" "start_stop" {
  statement {
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = [var.instance_arn]
  }
  statement {
    actions   = ["rds:StartDBInstance", "rds:StopDBInstance"]
    resources = [var.db_instance_arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "start-stop"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.start_stop.json
}

locals {
  schedules = {
    db-start  = { cron = var.start_cron, arn = "arn:aws:scheduler:::aws-sdk:rds:startDBInstance", input = { DbInstanceIdentifier = var.db_instance_id } }
    app-start = { cron = var.start_cron_app, arn = "arn:aws:scheduler:::aws-sdk:ec2:startInstances", input = { InstanceIds = [var.instance_id] } }
    app-stop  = { cron = var.stop_cron, arn = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances", input = { InstanceIds = [var.instance_id] } }
    db-stop   = { cron = var.stop_cron, arn = "arn:aws:scheduler:::aws-sdk:rds:stopDBInstance", input = { DbInstanceIdentifier = var.db_instance_id } }
  }
}

resource "aws_scheduler_schedule" "this" {
  for_each                     = local.schedules
  name                         = "${var.name}-${each.key}"
  schedule_expression          = "cron(${each.value.cron})"
  schedule_expression_timezone = var.timezone
  flexible_time_window {
    mode = "OFF"
  }
  target {
    arn      = each.value.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode(each.value.input)
  }
}
