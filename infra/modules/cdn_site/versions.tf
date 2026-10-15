terraform {
  required_version = ">= 1.11"
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 6.40"
      configuration_aliases = [aws.us_east_1] # CloudFront certificates must live in us-east-1
    }
  }
}
