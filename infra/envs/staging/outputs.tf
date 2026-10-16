output "name_servers" {
  value = module.stack.name_servers
}

output "site_url" {
  value = module.stack.site_url
}

output "api_url" {
  value = module.stack.api_url
}

output "instance_id" {
  value = module.stack.instance_id
}

output "app_secret_name" {
  value = module.stack.app_secret_name
}

output "db_instance_id" {
  value = module.stack.db_instance_id
}

output "github_environment_variables" {
  value = module.stack.github_environment_variables
}

output "github_connection_arn" {
  value = module.stack.github_connection_arn
}

output "deploy_project" {
  value = module.stack.deploy_project
}
