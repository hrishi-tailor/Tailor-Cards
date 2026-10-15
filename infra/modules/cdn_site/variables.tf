variable "name" {
  description = "Bucket and distribution name, e.g. tailorcards-staging-web"
  type        = string
}

variable "domain" {
  description = "Custom domain served by CloudFront, e.g. staging.tailorcards.com"
  type        = string
}

variable "zone_id" {
  type = string
}

variable "bucket_suffix" {
  description = "Makes the bucket name globally unique (e.g. the account id)"
  type        = string
}

variable "spa" {
  description = "Serve index.html for unknown paths (React router)"
  type        = bool
  default     = false
}

variable "content_security_policy" {
  description = "Content-Security-Policy header, or null for none"
  type        = string
  default     = null
}

variable "extra_headers" {
  description = "Other response headers, e.g. Permissions-Policy"
  type        = map(string)
  default     = {}
}

variable "price_class" {
  type    = string
  default = "PriceClass_100"
}

variable "force_destroy" {
  description = "Allow destroying the bucket with files in it"
  type        = bool
  default     = false
}
