# The app's secrets (ADMIN_PASSWORD, TOTP_ENCRYPTION_KEY, ANTHROPIC_API_KEY, ...) as one JSON secret. Terraform only
# creates the container: values are set with the AWS CLI (see infra/README.md), so they never reach the Terraform
# state, plans or CI logs. start.sh exports every key as an environment variable when the service starts.
resource "aws_secretsmanager_secret" "app" {
  name                    = "${var.name}/app"
  description             = "Tailor Cards ${var.name} application secrets (JSON of environment variables)"
  recovery_window_in_days = var.recovery_window_days
}
