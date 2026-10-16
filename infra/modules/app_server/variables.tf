variable "name" {
  type = string
}

variable "region" {
  type = string
}

variable "partition" {
  description = "AWS partition for managed policy ARNs"
  type        = string
  default     = "aws"
}

variable "vpc_id" {
  type = string
}

variable "vpc_cidr_block" {
  type = string
}

variable "public_subnet_id" {
  type = string
}

variable "instance_type" {
  description = "t4g.micro: 2 ARM vCPU (burst), 1 GiB RAM (plus 2 GiB swap); t4g.small has 2 GiB"
  type        = string
  default     = "t4g.micro"
}

variable "root_volume_gb" {
  type    = number
  default = 12
}

variable "ami_id" {
  description = "Amazon Linux 2023 ARM image; null looks up the latest from SSM"
  type        = string
  default     = null
}

variable "zone_id" {
  description = "Route 53 zone for the API record"
  type        = string
}

variable "api_domain" {
  description = "e.g. api.staging.tailorcards.com"
  type        = string
}

variable "frontend_url" {
  description = "Where Stripe returns customers after checkout"
  type        = string
}

variable "letsencrypt_email" {
  description = "Let's Encrypt account email (expiry notices)"
  type        = string
}

variable "images_bucket_name" {
  type = string
}

variable "images_bucket_arn" {
  type = string
}

variable "images_public_url" {
  type = string
}

variable "email_from" {
  type = string
}

variable "ses_identity_arn" {
  type = string
}

variable "db_secret_arn" {
  type = string
}

variable "app_secret_arn" {
  type = string
}

variable "log_group_names" {
  type = object({
    app          = string
    nginx_access = string
    nginx_error  = string
  })
}

variable "bucket_suffix" {
  description = "Makes bucket names globally unique (e.g. the account id)"
  type        = string
}
