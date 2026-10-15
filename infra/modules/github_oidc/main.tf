# Lets GitHub Actions deploy without stored AWS keys: the workflow presents a short-lived OIDC token from GitHub,
# and AWS lets it assume this role only when the token says it comes from this repository's "<environment>"
# environment. The role can do exactly what a deploy needs and nothing else.

resource "aws_iam_openid_connect_provider" "github" {
  count          = var.create_oidc_provider ? 1 : 0
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

locals {
  oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.existing_oidc_provider_arn
}

data "aws_iam_policy_document" "trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name                 = "${var.name}-github-deploy"
  assume_role_policy   = data.aws_iam_policy_document.trust.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid       = "UploadJar"
    actions   = ["s3:PutObject"]
    resources = ["${var.artifacts_bucket_arn}/*"]
  }
  statement {
    sid       = "SyncFrontend"
    actions   = ["s3:PutObject", "s3:DeleteObject", "s3:GetObject"]
    resources = ["${var.frontend_bucket_arn}/*"]
  }
  statement {
    sid       = "ListFrontend"
    actions   = ["s3:ListBucket"]
    resources = [var.frontend_bucket_arn]
  }
  statement {
    sid       = "InvalidateFrontendCache"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [var.frontend_distribution_arn]
  }
  statement {
    sid     = "RunDeployScriptOnTheServer"
    actions = ["ssm:SendCommand"]
    resources = [
      var.instance_arn,
      "arn:${var.partition}:ssm:${var.region}::document/AWS-RunShellScript",
    ]
  }
  statement {
    sid       = "ReadDeployResult"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"] # these actions don't support resource-level permissions
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
