terraform {
  required_version = ">= 1.10, < 2.0"
  backend "s3" {}
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}
provider "aws" { region = var.region }
variable "region" { type = string }
variable "service_name" { type = string }
variable "environment" { type = string }
variable "owner" { type = string }
variable "subnet_ids" { type = list(string) }
variable "security_group_ids" { type = list(string) }
module "postgres" {
  source             = "../postgres"
  service_name       = var.service_name
  environment        = var.environment
  owner              = var.owner
  subnet_ids         = var.subnet_ids
  security_group_ids = var.security_group_ids
}
output "secret_arn" { value = module.postgres.secret_arn }
output "host" { value = module.postgres.host }
output "port" { value = module.postgres.port }
output "database" { value = module.postgres.database }
