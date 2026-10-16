output "connection_arn" {
  description = "Authorise this connection once in the console (Developer Tools > Settings > Connections)"
  value       = aws_codeconnections_connection.github.arn
}

output "project_name" {
  description = "aws codebuild start-build --project-name <this> deploys the branch now"
  value       = aws_codebuild_project.deploy.name
}
