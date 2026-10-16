output "address" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "instance_id" {
  value = aws_db_instance.this.identifier
}

output "instance_arn" {
  value = aws_db_instance.this.arn
}

output "secret_arn" {
  description = "Secrets Manager secret with username, password, host, port and dbname"
  value       = aws_secretsmanager_secret.db.arn
}
