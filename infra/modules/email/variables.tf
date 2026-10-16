variable "domain" {
  description = "Domain to send from, e.g. staging.tailorcards.com"
  type        = string
}

variable "zone_id" {
  type = string
}

variable "dmarc_report_email" {
  description = "Where DMARC aggregate reports go"
  type        = string
}
