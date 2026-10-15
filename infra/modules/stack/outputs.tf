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
  description = "Set these as variables of the GitHub environment used by the deploy workflow"
  value = {
    AWS_DEPLOY_ROLE_ARN      = module.github_oidc.role_arn
    AWS_REGION               = var.region
    ARTIFACTS_BUCKET         = module.app_server.artifacts_bucket
    FRONTEND_BUCKET          = module.frontend.bucket
    FRONTEND_DISTRIBUTION_ID = module.frontend.distribution_id
    INSTANCE_ID              = module.app_server.instance_id
    API_URL                  = "https://${local.api_domain}"
  }
}
