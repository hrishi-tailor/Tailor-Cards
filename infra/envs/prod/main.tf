# PROD on AWS: the same stack with production sizing and protection. It is NOT the current production (Render,
# Vercel, Supabase) and nothing here touches it. Kept so staging and prod stay identical in shape; apply only when
# moving production to AWS.
module "stack" {
  source    = "../../modules/stack"
  providers = { aws = aws, aws.us_east_1 = aws.us_east_1 }

  environment        = "prod"
  region             = var.region
  domain             = var.domain
  azs                = var.azs
  vpc_cidr_block     = "10.30.0.0/16"
  bucket_suffix      = var.bucket_suffix
  alert_email        = var.alert_email
  ami_id             = var.ami_id
  vercel_config_path = "${path.root}/../../../frontend/vercel.json"

  # One GitHub OIDC provider per account: staging created it
  create_oidc_provider       = false
  existing_oidc_provider_arn = var.github_oidc_provider_arn

  instance_type         = "t4g.small"
  db_instance_class     = "db.t4g.small"
  db_multi_az           = true
  backup_retention_days = 14
  deletion_protection   = true
  log_retention_days    = 90
  monthly_budget_usd    = "80"
}
