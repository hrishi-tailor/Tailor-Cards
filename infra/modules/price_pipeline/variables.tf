variable "name" {
  description = "Prefix for every resource, e.g. tailorcards-staging"
  type        = string
}

variable "environment" {
  description = "Reported as the Environment dimension of the pipeline's metrics"
  type        = string
}

variable "app_base_url" {
  description = "The app the pipeline prices for and reports to, e.g. https://api.example.com"
  type        = string
}

variable "source_dir" {
  description = "The pipeline/ directory of this repository (the price_ingest package is zipped from it)"
  type        = string
}

variable "bucket_suffix" {
  description = "Keeps the data-lake bucket name globally unique"
  type        = string
}

variable "alerts_topic_arn" {
  description = "SNS topic for the pipeline's alarms"
  type        = string
}

variable "schedule_cron" {
  description = "When the daily run starts (minutes hours day-of-month month day-of-week year)"
  type        = string
  default     = "0 3 * * ? *"
}

variable "timezone" {
  type    = string
  default = "America/Toronto"
}

variable "max_requests_per_second" {
  description = "Ceiling on calls to TCGdex"
  type        = number
  default     = 8
}

variable "max_error_ratio" {
  description = "Share of cards that may fail before the run counts as failed"
  type        = number
  default     = 0.2
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "force_destroy" {
  description = "Let terraform destroy delete the data-lake bucket with its contents (staging only)"
  type        = bool
  default     = false
}

variable "recovery_window_days" {
  description = "Days a deleted shared secret can be restored (0 deletes it at once)"
  type        = number
  default     = 0
}
