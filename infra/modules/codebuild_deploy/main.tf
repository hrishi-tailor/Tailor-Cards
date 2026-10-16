# Continuous deployment from inside AWS: CodeBuild clones the repository, runs the tests and deploys
# (buildspec-staging.yml). Its IAM role supplies the credentials, so no AWS keys are stored anywhere. Used where
# GitHub's OIDC provider can't be created (projects on the new AWS experience deny iam:*Provider*).
#
# Two ways to reach GitHub:
# - use_github_connection = false (default): clones the public repository anonymously. Deploys start with
#   `aws codebuild start-build` (no webhook: webhooks need GitHub credentials). On the Free-plan project this was
#   built for, CodeBuild rejects CodeConnections at CreateProject ("User is not authorized to access connection"),
#   even with an Available connection and a role allowed to use it.
# - use_github_connection = true: a CodeConnections GitHub connection (authorise it once in the console, Developer
#   Tools > Settings > Connections) and a webhook that deploys on every push. Needed for a private repository.

resource "aws_codeconnections_connection" "github" {
  count         = var.use_github_connection ? 1 : 0
  name          = substr("${var.name}-github", 0, 32)
  provider_type = "GitHub"
}

locals {
  clone_buildspec = yamlencode({
    version = "0.2"
    phases = {
      install = { "runtime-versions" = { java = "corretto21", nodejs = 22 } }
      # Not `git clone .`: the cache has already put frontend/node_modules here, so the directory isn't empty
      pre_build = { commands = [
        "git init -q .",
        "git fetch -q --depth 1 https://github.com/${var.github_repository}.git ${var.branch}",
        "git checkout -q FETCH_HEAD",
      ] }
      build = { commands = [
        "export VERSION=\"$(git rev-parse --short=12 HEAD)\"",
        "./mvnw -B verify",
        "bash infra/scripts/deploy-staging.sh",
      ] }
    }
    cache = { paths = ["/root/.m2/**/*", "frontend/node_modules/**/*"] }
  })
}

resource "aws_cloudwatch_log_group" "build" {
  name              = "/${var.name}/deploy"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["codebuild.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "build" {
  name               = "${var.name}-deploy"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

data "aws_iam_policy_document" "build" {
  statement {
    sid       = "Logs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.build.arn}:*"]
  }
  dynamic "statement" {
    for_each = aws_codeconnections_connection.github
    content {
      sid       = "UseGitHubConnection"
      actions   = ["codeconnections:GetConnection", "codeconnections:GetConnectionToken", "codeconnections:UseConnection", "codestar-connections:UseConnection"]
      resources = [statement.value.arn]
    }
  }
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

resource "aws_iam_role_policy" "build" {
  name   = "deploy"
  role   = aws_iam_role.build.id
  policy = data.aws_iam_policy_document.build.json
}

resource "aws_codebuild_project" "deploy" {
  # checkov:skip=CKV_AWS_316: Docker is needed for the PostgreSQL integration tests; the build only runs this repository's main branch
  # CodeBuild checks the role can use the connection when the project is created
  depends_on             = [aws_iam_role_policy.build]
  name                   = "${var.name}-deploy"
  description            = "Test and deploy ${var.github_repository} (${var.branch}) to ${var.name}"
  service_role           = aws_iam_role.build.arn
  build_timeout          = 40
  queued_timeout         = 60
  concurrent_build_limit = 1 # deploys never overlap

  # With a connection: CodeBuild checks out the branch and runs the repository's buildspec. Without one: an empty
  # workspace and a buildspec that clones the public repository itself, then runs the same steps.
  dynamic "source" {
    for_each = aws_codeconnections_connection.github
    content {
      type            = "GITHUB"
      location        = "https://github.com/${var.github_repository}.git"
      buildspec       = "buildspec-staging.yml"
      git_clone_depth = 1
      auth {
        type     = "CODECONNECTIONS"
        resource = source.value.arn
      }
    }
  }
  dynamic "source" {
    for_each = var.use_github_connection ? [] : [1]
    content {
      type      = "NO_SOURCE"
      buildspec = local.clone_buildspec
    }
  }
  source_version = var.use_github_connection ? var.branch : null

  environment {
    type            = "ARM_CONTAINER" # Graviton: cheaper per minute
    compute_type    = "BUILD_GENERAL1_SMALL"
    image           = "aws/codebuild/amazonlinux-aarch64-standard:3.0"
    privileged_mode = true # Docker, for the PostgreSQL integration tests (Testcontainers)

    dynamic "environment_variable" {
      for_each = var.deploy_variables
      content {
        name  = environment_variable.key
        value = environment_variable.value
      }
    }
  }

  artifacts {
    type = "NO_ARTIFACTS"
  }

  cache {
    type = "LOCAL"
    # The source cache needs a source CodeBuild checks out itself
    modes = concat(var.use_github_connection ? ["LOCAL_SOURCE_CACHE"] : [], ["LOCAL_DOCKER_LAYER_CACHE", "LOCAL_CUSTOM_CACHE"])
  }

  logs_config {
    cloudwatch_logs {
      group_name = aws_cloudwatch_log_group.build.name
    }
  }
}

# Build on every push to the branch; needs the connection authorised first
resource "aws_codebuild_webhook" "push" {
  count        = var.use_github_connection && var.enable_webhook ? 1 : 0
  project_name = aws_codebuild_project.deploy.name
  build_type   = "BUILD"
  filter_group {
    filter {
      type    = "EVENT"
      pattern = "PUSH"
    }
    filter {
      type    = "HEAD_REF"
      pattern = "^refs/heads/${var.branch}$"
    }
  }
}
