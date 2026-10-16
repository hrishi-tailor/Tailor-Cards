variable "name" {
  description = "Instance identifier and name prefix, e.g. tailorcards-staging"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  description = "Subnets in at least two availability zones, with no internet route"
  type        = list(string)
}

variable "app_security_group_id" {
  description = "Security group of the app server (the only thing allowed to connect)"
  type        = string
}

variable "engine_major_version" {
  description = "PostgreSQL major version (minor upgrades are automatic)"
  type        = string
  default     = "17"
}

variable "instance_class" {
  description = "db.t4g.micro is the smallest Graviton class (2 vCPU burst, 1 GiB)"
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "max_allocated_storage_gb" {
  description = "Storage autoscaling ceiling"
  type        = number
  default     = 50
}

variable "db_name" {
  type    = string
  default = "tailorcards"
}

variable "username" {
  type    = string
  default = "tailorcards"
}

variable "password_version" {
  description = "Bump to generate and apply a new database password"
  type        = number
  default     = 1
}

variable "backup_retention_days" {
  description = "Automated backups (point-in-time restore window)"
  type        = number
  default     = 7
}

variable "multi_az" {
  description = "Standby in a second AZ (doubles the instance cost)"
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Refuse to delete the database (and keep the secret recoverable for 30 days)"
  type        = bool
  default     = false
}
