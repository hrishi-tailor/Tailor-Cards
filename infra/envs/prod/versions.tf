terraform {
  required_version = ">= 1.11" # write-only arguments keep the database password out of state

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.40"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # Remote state in S3 with S3-native locking. Values come from backend.hcl (see backend.hcl.example):
  #   terraform init -backend-config=backend.hcl
  backend "s3" {}
}
