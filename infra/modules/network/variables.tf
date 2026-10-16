variable "name" {
  description = "Name prefix for resources"
  type        = string
}

variable "region" {
  description = "AWS region (for endpoint service names)"
  type        = string
}

variable "cidr_block" {
  description = "VPC address range"
  type        = string
  default     = "10.20.0.0/16"
}

variable "azs" {
  description = "Availability zones: two are needed for an RDS subnet group"
  type        = list(string)
  validation {
    condition     = length(var.azs) >= 2
    error_message = "RDS needs subnets in at least two availability zones."
  }
}

variable "enable_interface_endpoints" {
  description = "Interface endpoints for SSM, Secrets Manager and CloudWatch Logs (~$7.30/month each per AZ)"
  type        = bool
  default     = false
}

variable "enable_flow_logs" {
  description = "Log rejected traffic to CloudWatch (flow_log_group_arn and flow_log_role_arn)"
  type        = bool
  default     = true
}

variable "flow_log_group_arn" {
  description = "CloudWatch log group for VPC flow logs (rejected traffic)"
  type        = string
  default     = null
}

variable "flow_log_role_arn" {
  description = "IAM role that lets VPC flow logs write to the log group"
  type        = string
  default     = null
}
