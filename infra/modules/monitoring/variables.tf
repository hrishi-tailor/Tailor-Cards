variable "name" {
  type = string
}

variable "alert_email" {
  description = "Receives alarm and budget emails"
  type        = string
}

variable "instance_id" {
  type = string
}

variable "db_instance_id" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "monthly_budget_usd" {
  type    = string
  default = "10"
}

variable "budget_tag" {
  description = "Limit the budget to resources with this cost-allocation tag (activate it in Billing first); null counts the whole account"
  type = object({
    key   = string
    value = string
  })
  default = null
}
