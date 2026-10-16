variable "region" {
  type    = string
  default = "us-east-2"
}

variable "azs" {
  type    = list(string)
  default = ["us-east-2a", "us-east-2b"]
}

variable "domain" {
  description = "Production domain on AWS"
  type        = string
}

variable "bucket_suffix" {
  type = string
}

variable "alert_email" {
  type = string
}

variable "ami_id" {
  type    = string
  default = null
}

variable "github_oidc_provider_arn" {
  description = "An existing GitHub OIDC provider, to deploy from GitHub Actions instead of CodeBuild"
  type        = string
  default     = null
}
