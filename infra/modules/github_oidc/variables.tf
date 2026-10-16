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
  description = "owner/repo, e.g. hrishi-tailor/Tailor-Cards"
  type        = string
}

variable "github_environment" {
  description = "GitHub environment the deploy job runs in (its OIDC token names it)"
  type        = string
}

variable "create_oidc_provider" {
  description = "An account has one GitHub OIDC provider: create it in the first environment, reuse it in others"
  type        = bool
  default     = true
}

variable "existing_oidc_provider_arn" {
  type    = string
  default = null
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
