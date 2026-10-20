output "bucket" {
  value = aws_s3_bucket.lake.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.lake.arn
}

output "function_name" {
  value = aws_lambda_function.this.function_name
}

output "shared_secret_arn" {
  value = aws_secretsmanager_secret.shared.arn
}
