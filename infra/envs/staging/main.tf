# STAGING: a full copy of Tailor Cards on AWS, separate from production (Render, Vercel, Supabase), which this does
# not touch. Sized for the lowest cost: see infra/README.md for the monthly estimate.
module "stack" {
  source    = "../../modules/stack"
  providers = { aws = aws, aws.us_east_1 = aws.us_east_1 }

  environment        = "staging"
  region             = var.region
  domain             = var.domain
  azs                = var.azs
  bucket_suffix      = var.bucket_suffix
  alert_email        = var.alert_email
  ami_id             = var.ami_id
  vercel_config_path = "${path.root}/../../../frontend/vercel.json"

  instance_type         = "t4g.micro"
  db_instance_class     = "db.t4g.micro"
  db_multi_az           = false
  backup_retention_days = 1 # Free-plan accounts reject longer automated backup retention
  deletion_protection   = false
  enable_scheduler      = var.enable_scheduler
  enable_price_pipeline = var.enable_price_pipeline
  enable_warehouse      = var.enable_warehouse
  enable_weekly_report  = var.enable_weekly_report
  # The warehouse reads production's ledger: set price_pipeline_app_url in terraform.tfvars (docs/warehouse.md)
  price_pipeline_app_url = var.price_pipeline_app_url
  deploy_branch          = var.deploy_branch
  monthly_budget_usd     = "30"
}
