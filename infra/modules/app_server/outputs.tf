output "instance_id" {
  value = aws_instance.app.id
}

output "security_group_id" {
  value = aws_security_group.app.id
}

output "public_ip" {
  value = aws_eip.app.public_ip
}

output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.bucket
}

output "artifacts_bucket_arn" {
  value = aws_s3_bucket.artifacts.arn
}

output "role_name" {
  value = aws_iam_role.app.name
}

output "instance_arn" {
  value = aws_instance.app.arn
}
