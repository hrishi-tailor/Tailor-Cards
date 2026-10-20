terraform {
  required_version = ">= 1.11"
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 6.40"
      configuration_aliases = [aws.us_east_1]
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
}
