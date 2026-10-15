output "log_group_names" {
  value = {
    app          = aws_cloudwatch_log_group.this["app"].name
    nginx_access = aws_cloudwatch_log_group.this["nginx-access"].name
    nginx_error  = aws_cloudwatch_log_group.this["nginx-error"].name
  }
}

output "flow_log_group_arn" {
  value = aws_cloudwatch_log_group.this["vpc-flow-rejected"].arn
}

output "flow_log_role_arn" {
  value = aws_iam_role.flow_logs.arn
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
