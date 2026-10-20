# One complete Tailor Cards environment. envs/staging and envs/prod call this with their own sizes and names.

locals {
  name         = "tailorcards-${var.environment}"
  api_domain   = "api.${var.domain}"
  image_domain = "images.${var.domain}"
  email_from   = "no-reply@${var.domain}"

  # The frontend gets the same security headers as production on Vercel (frontend/vercel.json), with the API host
  # swapped for this environment's
  vercel_headers = { for h in jsondecode(file(var.vercel_config_path)).headers[0].headers : h.key => h.value }
  frontend_csp   = replace(local.vercel_headers["Content-Security-Policy"], "https://tailor-cards-api.onrender.com", "https://${local.api_domain}")

  # What infra/scripts/deploy-staging.sh needs, wherever it runs (CodeBuild or GitHub Actions)
  deploy_variables = {
    AWS_REGION               = var.region
    ARTIFACTS_BUCKET         = module.app_server.artifacts_bucket
    FRONTEND_BUCKET          = module.frontend.bucket
    FRONTEND_DISTRIBUTION_ID = module.frontend.distribution_id
    INSTANCE_ID              = module.app_server.instance_id
    API_URL                  = "https://${local.api_domain}"
  }
}

module "dns" {
  source = "../dns"
  domain = var.domain
}

module "monitoring" {
  source             = "../monitoring"
  name               = local.name
  alert_email        = var.alert_email
  instance_id        = module.app_server.instance_id
  db_instance_id     = module.database.instance_id
  log_retention_days = var.log_retention_days
  monthly_budget_usd = var.monthly_budget_usd
}

module "network" {
  source                     = "../network"
  name                       = local.name
  region                     = var.region
  cidr_block                 = var.vpc_cidr_block
  azs                        = var.azs
  enable_interface_endpoints = var.enable_interface_endpoints
  flow_log_group_arn         = module.monitoring.flow_log_group_arn
  flow_log_role_arn          = module.monitoring.flow_log_role_arn
}

module "secrets" {
  source               = "../secrets"
  name                 = local.name
  recovery_window_days = var.deletion_protection ? 30 : 0
}

module "email" {
  source             = "../email"
  domain             = var.domain
  zone_id            = module.dns.zone_id
  dmarc_report_email = var.alert_email
}

module "frontend" {
  source                  = "../cdn_site"
  providers               = { aws = aws, aws.us_east_1 = aws.us_east_1 }
  name                    = "${local.name}-web"
  domain                  = var.domain
  zone_id                 = module.dns.zone_id
  bucket_suffix           = var.bucket_suffix
  spa                     = true
  content_security_policy = local.frontend_csp
  extra_headers = {
    "Permissions-Policy"         = local.vercel_headers["Permissions-Policy"]
    "Cross-Origin-Opener-Policy" = local.vercel_headers["Cross-Origin-Opener-Policy"]
  }
  force_destroy = !var.deletion_protection
}

module "images" {
  source        = "../cdn_site"
  providers     = { aws = aws, aws.us_east_1 = aws.us_east_1 }
  name          = "${local.name}-images"
  domain        = local.image_domain
  zone_id       = module.dns.zone_id
  bucket_suffix = var.bucket_suffix
  spa           = false
  force_destroy = !var.deletion_protection
}

module "app_server" {
  source             = "../app_server"
  name               = local.name
  region             = var.region
  vpc_id             = module.network.vpc_id
  vpc_cidr_block     = module.network.cidr_block
  public_subnet_id   = module.network.public_subnet_ids[0]
  instance_type      = var.instance_type
  ami_id             = var.ami_id
  zone_id            = module.dns.zone_id
  api_domain         = local.api_domain
  frontend_url       = module.frontend.url
  letsencrypt_email  = var.alert_email
  images_bucket_name = module.images.bucket
  images_bucket_arn  = module.images.bucket_arn
  images_public_url  = module.images.url
  email_from         = local.email_from
  ses_identity_arn   = module.email.identity_arn
  db_secret_arn      = module.database.secret_arn
  app_secret_arn     = module.secrets.app_secret_arn
  log_group_names    = module.monitoring.log_group_names
  bucket_suffix      = var.bucket_suffix
}

module "database" {
  source                = "../database"
  name                  = local.name
  vpc_id                = module.network.vpc_id
  private_subnet_ids    = module.network.private_subnet_ids
  app_security_group_id = module.app_server.security_group_id
  instance_class        = var.db_instance_class
  multi_az              = var.db_multi_az
  backup_retention_days = var.backup_retention_days
  deletion_protection   = var.deletion_protection
}

module "github_oidc" {
  count                      = var.enable_github_oidc ? 1 : 0
  source                     = "../github_oidc"
  name                       = local.name
  region                     = var.region
  github_repository          = var.github_repository
  github_environment         = var.environment
  create_oidc_provider       = var.create_oidc_provider
  existing_oidc_provider_arn = var.existing_oidc_provider_arn
  artifacts_bucket_arn       = module.app_server.artifacts_bucket_arn
  frontend_bucket_arn        = module.frontend.bucket_arn
  frontend_distribution_arn  = module.frontend.distribution_arn
  instance_arn               = module.app_server.instance_arn
}

module "codebuild_deploy" {
  count                     = var.enable_codebuild_deploy ? 1 : 0
  source                    = "../codebuild_deploy"
  name                      = local.name
  region                    = var.region
  github_repository         = var.github_repository
  branch                    = var.deploy_branch
  use_github_connection     = var.use_github_connection
  enable_webhook            = var.enable_deploy_webhook
  artifacts_bucket_arn      = module.app_server.artifacts_bucket_arn
  frontend_bucket_arn       = module.frontend.bucket_arn
  frontend_distribution_arn = module.frontend.distribution_arn
  instance_arn              = module.app_server.instance_arn
  deploy_variables          = local.deploy_variables
  log_retention_days        = var.log_retention_days
}

module "scheduler" {
  count           = var.enable_scheduler ? 1 : 0
  source          = "../scheduler"
  name            = local.name
  instance_id     = module.app_server.instance_id
  instance_arn    = module.app_server.instance_arn
  db_instance_id  = module.database.instance_id
  db_instance_arn = module.database.instance_arn
}

module "price_pipeline" {
  count              = var.enable_price_pipeline ? 1 : 0
  source             = "../price_pipeline"
  name               = local.name
  environment        = var.environment
  app_base_url       = coalesce(var.price_pipeline_app_url, "https://${local.api_domain}")
  source_dir         = "${path.module}/../../../pipeline"
  bucket_suffix      = var.bucket_suffix
  alerts_topic_arn   = module.monitoring.alerts_topic_arn
  schedule_cron      = var.price_pipeline_schedule
  log_retention_days = var.log_retention_days
  force_destroy      = !var.deletion_protection
}

module "warehouse" {
  count              = var.enable_price_pipeline && var.enable_warehouse ? 1 : 0
  source             = "../warehouse"
  name               = local.name
  environment        = var.environment
  app_base_url       = coalesce(var.price_pipeline_app_url, "https://${local.api_domain}")
  source_dir         = "${path.module}/../../../pipeline"
  bucket             = module.price_pipeline[0].bucket
  bucket_arn         = module.price_pipeline[0].bucket_arn
  shared_secret_arn  = module.price_pipeline[0].shared_secret_arn
  alerts_topic_arn   = module.monitoring.alerts_topic_arn
  log_retention_days = var.log_retention_days
}
