variable "name" {
  type = string
}

variable "recovery_window_days" {
  description = "0 deletes immediately on destroy (staging); 7-30 keeps it recoverable (prod)"
  type        = number
  default     = 0
}
