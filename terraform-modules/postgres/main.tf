variable "service_name" { type = string }
variable "environment"  { type = string }
resource "random_password" "db" { length = 24 }
resource "aws_db_instance" "this" {
  identifier          = "${var.service_name}-${var.environment}"
  engine              = "postgres"
  instance_class      = "db.t3.micro"
  allocated_storage   = 20
  username            = replace(var.service_name, "-", "_")
  password            = random_password.db.result
  skip_final_snapshot = var.environment == "dev"
}
resource "aws_secretsmanager_secret" "db" { name = "${var.service_name}/${var.environment}/db" }
resource "aws_secretsmanager_secret_version" "db" {
  secret_id     = aws_secretsmanager_secret.db.id
  secret_string = random_password.db.result
}
output "secret_ref" { value = aws_secretsmanager_secret.db.name }
