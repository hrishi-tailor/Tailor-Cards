variable "environment" {
  description = "staging or prod (names resources and the GitHub environment allowed to deploy)"
  type        = string
  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be staging or prod."
  }
}

variable "region" {
  type = string
}

variable "domain" {
  description = "The environment's domain: the site is served here, the API at api.<domain>, images at images.<domain>"
  type        = string
}

variable "azs" {
  type = list(string)
}

variable "vpc_cidr_block" {
  type    = string
  default = "10.20.0.0/16"
}

variable "bucket_suffix" {
  description = "Makes S3 bucket names globally unique; use the AWS account id"
  type        = string
}

variable "alert_email" {
  description = "Alarms, budget alerts, Let's Encrypt notices and DMARC reports"
  type        = string
}

variable "github_repository" {
  type    = string
  default = "hrishi-tailor/Tailor-Cards"
}

variable "enable_github_oidc" {
  description = "GitHub Actions deploy role via OIDC (accounts on the new AWS experience deny creating OIDC providers)"
  type        = bool
  default     = false
}

variable "enable_codebuild_deploy" {
  description = "Deploy from CodeBuild on every push (works where OIDC providers are denied)"
  type        = bool
  default     = true
}

variable "use_github_connection" {
  description = "CodeBuild reaches GitHub through a CodeConnections connection and deploys on every push (see modules/codebuild_deploy)"
  type        = bool
  default     = false
}

variable "enable_deploy_webhook" {
  description = "Turn on after authorising the GitHub connection in the console"
  type        = bool
  default     = true
}

variable "deploy_branch" {
  type    = string
  default = "main"
}

variable "create_oidc_provider" {
  type    = bool
  default = true
}

variable "existing_oidc_provider_arn" {
  type    = string
  default = null
}

variable "instance_type" {
  type    = string
  default = "t4g.micro"
}

variable "ami_id" {
  type    = string
  default = null
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "backup_retention_days" {
  type    = number
  default = 7
}

variable "deletion_protection" {
  type    = bool
  default = false
}

variable "enable_interface_endpoints" {
  type    = bool
  default = false
}

variable "enable_scheduler" {
  description = "Stop the server and database outside 8am-8pm Toronto time on weekdays"
  type        = bool
  default     = false
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "monthly_budget_usd" {
  type    = string
  default = "10"
}

variable "vercel_config_path" {
  description = "frontend/vercel.json, the source of the frontend's security headers"
  type        = string
}

variable "enable_price_pipeline" {
  description = "Run the daily AWS price pipeline (Lambda, S3 data lake, schedule, alarms)"
  type        = bool
  default     = false
}

variable "price_pipeline_app_url" {
  description = "The app the price pipeline and warehouse read from and report to; null means this environment's own API"
  type        = string
  default     = null
}

variable "price_pipeline_schedule" {
  description = "Daily start, America/Toronto (minutes hours day-of-month month day-of-week year)"
  type        = string
  default     = "0 3 * * ? *"
}

variable "enable_warehouse" {
  description = "Run the nightly warehouse refresh (Glue catalog, Athena, Lambda); needs enable_price_pipeline"
  type        = bool
  default     = false
}

variable "enable_weekly_report" {
  description = "Email last week's results every Monday (needs enable_warehouse)"
  type        = bool
  default     = false
}

variable "report_recipients" {
  description = "Who gets the Monday email; null means alert_email"
  type        = list(string)
  default     = null
}
