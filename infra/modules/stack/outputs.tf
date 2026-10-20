output "name_servers" {
  description = "Delegate the domain: add these NS records for it at the parent domain's DNS provider"
  value       = module.dns.name_servers
}

output "site_url" {
  value = module.frontend.url
}

output "api_url" {
  value = "https://${local.api_domain}"
}

output "images_url" {
  value = module.images.url
}

output "instance_id" {
  description = "aws ssm start-session --target <this>"
  value       = module.app_server.instance_id
}

output "app_secret_name" {
  value = module.secrets.app_secret_name
}

output "db_instance_id" {
  value = module.database.instance_id
}

output "github_environment_variables" {
  description = "Variables of the GitHub environment used by deploy-staging.yml (only with enable_github_oidc)"
  value = var.enable_github_oidc ? merge(local.deploy_variables, {
    AWS_DEPLOY_ROLE_ARN = module.github_oidc[0].role_arn
  }) : null
}

output "github_connection_arn" {
  description = "Authorise once in the console: Developer Tools > Settings > Connections"
  value       = var.enable_codebuild_deploy ? module.codebuild_deploy[0].connection_arn : null
}

output "deploy_project" {
  description = "aws codebuild start-build --project-name <this> deploys now"
  value       = var.enable_codebuild_deploy ? module.codebuild_deploy[0].project_name : null
}

output "price_pipeline" {
  description = "Where the price pipeline lands data and which secret it signs with (null when disabled)"
  value = var.enable_price_pipeline ? {
    bucket            = module.price_pipeline[0].bucket
    function_name     = module.price_pipeline[0].function_name
    shared_secret_arn = module.price_pipeline[0].shared_secret_arn
  } : null
}

output "warehouse" {
  description = "The Glue database and Athena workgroup to query (null when disabled)"
  value = var.enable_price_pipeline && var.enable_warehouse ? {
    database      = module.warehouse[0].database
    workgroup     = module.warehouse[0].workgroup
    function_name = module.warehouse[0].function_name
    report        = module.warehouse[0].report_function_name
  } : null
}
