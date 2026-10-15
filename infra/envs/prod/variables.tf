variable "region" {
  type    = string
  default = "us-east-1"
}

variable "azs" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b"]
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
  description = "The GitHub OIDC provider created by the staging environment"
  type        = string
}
