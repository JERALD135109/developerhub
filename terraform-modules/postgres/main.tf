terraform {
  required_version = ">= 1.10, < 2.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}
variable "service_name" {
  type = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,40}$", var.service_name))
    error_message = "Use a validated service name."
  }
}
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging or prod."
  }
}
variable "subnet_ids" { type = list(string) }
variable "security_group_ids" { type = list(string) }
variable "owner" { type = string }
resource "aws_db_subnet_group" "this" {
  name       = "${var.service_name}-${var.environment}"
  subnet_ids = var.subnet_ids
}
resource "aws_db_instance" "this" {
  identifier                  = "${var.service_name}-${var.environment}"
  engine                      = "postgres"
  instance_class              = "db.t3.micro"
  allocated_storage           = 20
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = replace(var.service_name, "-", "_")
  username                    = "service_admin"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.this.name
  vpc_security_group_ids      = var.security_group_ids
  publicly_accessible         = false
  backup_retention_period     = var.environment == "dev" ? 1 : 7
  deletion_protection         = var.environment == "prod"
  skip_final_snapshot         = var.environment == "dev"
  final_snapshot_identifier   = "${var.service_name}-${var.environment}-final"
  tags                        = { Owner = var.owner, Environment = var.environment, ManagedBy = "DeveloperHub" }
}
# Store only connection metadata and a reference to the RDS-managed credential.
# The workflow uses the managed secret ARN directly, with separate endpoint data.
output "secret_arn" { value = aws_db_instance.this.master_user_secret[0].secret_arn }
output "host" { value = aws_db_instance.this.address }
output "port" { value = aws_db_instance.this.port }
output "database" { value = aws_db_instance.this.db_name }
