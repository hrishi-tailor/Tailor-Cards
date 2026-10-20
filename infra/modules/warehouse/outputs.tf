output "database" {
  value = aws_glue_catalog_database.this.name
}

output "workgroup" {
  value = aws_athena_workgroup.this.name
}

output "function_name" {
  value = aws_lambda_function.this.function_name
}
