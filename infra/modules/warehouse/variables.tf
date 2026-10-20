variable "name" {
  description = "Prefix for every resource, e.g. tailorcards-staging"
  type        = string
}

variable "environment" {
  type = string
}

variable "app_base_url" {
  description = "The app whose ledger, products and prices are exported"
  type        = string
}

variable "source_dir" {
  description = "The pipeline/ directory of this repository"
  type        = string
}

variable "bucket" {
  description = "The data-lake bucket (from the price pipeline module)"
  type        = string
}

variable "bucket_arn" {
  type = string
}

variable "shared_secret_arn" {
  description = "The signing secret shared with the app (from the price pipeline module)"
  type        = string
}

variable "alerts_topic_arn" {
  type = string
}

variable "schedule_cron" {
  description = "Nightly refresh, after the price pipeline (minutes hours day-of-month month day-of-week year)"
  type        = string
  default     = "0 4 * * ? *"
}

variable "timezone" {
  type    = string
  default = "America/Toronto"
}

variable "app_timeout_seconds" {
  description = "How long to wait for the app's first answer (a sleeping host can take most of a minute)"
  type        = number
  default     = 60
}

variable "bytes_scanned_cutoff" {
  description = "Athena cancels any query that would scan more than this (bytes)"
  type        = number
  default     = 1073741824
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "report_recipients" {
  description = "Who gets the Monday email; empty turns the report off"
  type        = list(string)
  default     = []
}

variable "report_from" {
  description = "Sender of the Monday email, an address on the SES identity"
  type        = string
  default     = null
}

variable "ses_identity_arn" {
  description = "The verified SES domain identity the report is sent from"
  type        = string
  default     = null
}

variable "report_cron" {
  description = "When the Monday email is sent"
  type        = string
  default     = "0 8 ? * MON *"
}
