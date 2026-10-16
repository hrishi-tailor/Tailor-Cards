variable "name" {
  type = string
}

variable "region" {
  type = string
}

variable "partition" {
  type    = string
  default = "aws"
}

variable "github_repository" {
  description = "owner/repo"
  type        = string
}

variable "branch" {
  type    = string
  default = "main"
}

variable "enable_webhook" {
  description = "Deploy on every push (turn on after authorising the GitHub connection)"
  type        = bool
  default     = true
}

variable "deploy_variables" {
  description = "Environment for infra/scripts/deploy-staging.sh (non-secret names and URLs)"
  type        = map(string)
}

variable "artifacts_bucket_arn" {
  type = string
}

variable "frontend_bucket_arn" {
  type = string
}

variable "frontend_distribution_arn" {
  type = string
}

variable "instance_arn" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 30
}
