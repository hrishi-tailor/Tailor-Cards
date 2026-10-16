# PostgreSQL on RDS in the private subnets: encrypted, automated daily backups, reachable only from the app server.
# The master password is generated as an ephemeral value and written only to RDS and Secrets Manager
# (write-only arguments), so it never appears in the Terraform state or plan.

ephemeral "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#%^*()-_=+[]{}:?" # characters RDS accepts in passwords
}

resource "aws_secretsmanager_secret" "db" {
  name                    = "${var.name}/database"
  description             = "Tailor Cards ${var.name} database credentials (written by Terraform, read by the app at start)"
  recovery_window_in_days = var.deletion_protection ? 30 : 0
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string_wo = jsonencode({
    username = var.username
    password = ephemeral.random_password.db.result
    host     = aws_db_instance.this.address
    port     = aws_db_instance.this.port
    dbname   = var.db_name
  })
  # Bump password_version to rotate: both the database and the secret get the new password in one apply
  secret_string_wo_version = var.password_version
}

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "PostgreSQL from the app server only"
  vpc_id      = var.vpc_id
  tags        = { Name = "${var.name}-db" }
}

resource "aws_vpc_security_group_ingress_rule" "from_app" {
  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL from the app server"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = var.app_security_group_id
}

resource "aws_db_parameter_group" "this" {
  name   = var.name
  family = "postgres${var.engine_major_version}"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot" # static parameter; otherwise every plan shows a change
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # log queries slower than 1 s
  }
  parameter {
    name  = "log_connections"
    value = "1"
  }
}

resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = var.engine_major_version
  instance_class = var.instance_class

  db_name             = var.db_name
  username            = var.username
  password_wo         = ephemeral.random_password.db.result
  password_wo_version = var.password_version

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period   = var.backup_retention_days
  backup_window             = "07:00-07:30" # UTC, 2-3 am Toronto
  maintenance_window        = "sun:08:00-sun:08:30"
  copy_tags_to_snapshot     = true
  delete_automated_backups  = false
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.name}-final"

  auto_minor_version_upgrade          = true
  iam_database_authentication_enabled = true
  enabled_cloudwatch_logs_exports     = ["postgresql"]
  apply_immediately                   = !var.deletion_protection
}
