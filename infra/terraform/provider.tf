terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

# Credentials come from your shell environment (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY) -
# never hardcoded here, same convention the app itself already uses for SENDGRID_API_KEY.
#
# IMPORTANT: this must be your own admin/deployer IAM user's keys, NOT the
# bodhsea-notification-service user's keys. This file creates the SQS queues, IAM roles/
# policies/groups, and Lambda functions themselves - bodhsea-notification-service only ever
# gets sqs:SendMessage on 3 specific queues (see iam.tf's app_send_only policy), which is
# nowhere near enough to run `terraform apply` and would fail with AccessDenied errors.
# bodhsea-notification-service's own keys are for the Spring Boot app's runtime (Render env
# vars), a completely separate credential from whatever runs Terraform here.
provider "aws" {
  region = var.aws_region
}
