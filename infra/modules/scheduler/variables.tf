variable "name" {
  type = string
}

variable "instance_id" {
  type = string
}

variable "instance_arn" {
  type = string
}

variable "db_instance_id" {
  type = string
}

variable "db_instance_arn" {
  type = string
}

variable "timezone" {
  type    = string
  default = "America/Toronto"
}

variable "start_cron" {
  description = "Database start (minutes hours day-of-month month day-of-week year)"
  type        = string
  default     = "0 8 ? * MON-FRI *"
}

variable "start_cron_app" {
  description = "App start, a few minutes after the database so it can connect"
  type        = string
  default     = "10 8 ? * MON-FRI *"
}

variable "stop_cron" {
  type    = string
  default = "0 20 ? * MON-FRI *"
}
