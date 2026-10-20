variable "region" {
  type    = string
  default = "us-east-2"
}

variable "azs" {
  description = "Two availability zones in the region"
  type        = list(string)
  default     = ["us-east-2a", "us-east-2b"]
}

variable "domain" {
  description = "Staging domain, e.g. staging.tailorcards.com"
  type        = string
}

variable "bucket_suffix" {
  description = "Your AWS account id (keeps bucket names globally unique)"
  type        = string
}

variable "alert_email" {
  type = string
}

variable "ami_id" {
  description = "Pin an Amazon Linux 2023 ARM image; null uses the latest"
  type        = string
  default     = null
}

variable "enable_scheduler" {
  description = "Stop the server and database nights and weekends (about 60% off their running cost)"
  type        = bool
  default     = false
}

variable "deploy_branch" {
  description = "Branch CodeBuild deploys. Staging tries work before it reaches main (main deploys production on Render)"
  type        = string
  default     = "main"
}

variable "enable_price_pipeline" {
  description = "Run the daily price pipeline against staging (see docs/price-pipeline.md)"
  type        = bool
  default     = false
}
