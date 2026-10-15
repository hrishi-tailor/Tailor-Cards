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
