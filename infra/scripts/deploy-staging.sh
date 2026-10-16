#!/bin/bash
# Deploys a tested build to staging: uploads the jar, runs deploy.sh on the app server through SSM Run Command
# (health check and automatic rollback there), then builds and publishes the frontend and refreshes CloudFront.
# Run from the repository root after `./mvnw verify`, with AWS credentials and these variables set:
#   AWS_REGION ARTIFACTS_BUCKET FRONTEND_BUCKET FRONTEND_DISTRIBUTION_ID INSTANCE_ID API_URL VERSION
set -euo pipefail
: "${AWS_REGION:?}" "${ARTIFACTS_BUCKET:?}" "${FRONTEND_BUCKET:?}" "${FRONTEND_DISTRIBUTION_ID:?}" "${INSTANCE_ID:?}" "${API_URL:?}" "${VERSION:?}"

echo "== Uploading the jar ($VERSION)"
jar=$(ls target/api-*.jar | head -n 1)
aws s3 cp "$jar" "s3://$ARTIFACTS_BUCKET/api/$VERSION.jar" --only-show-errors

echo "== Deploying on $INSTANCE_ID"
command_id=$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --comment "deploy $VERSION" \
  --timeout-seconds 600 \
  --parameters "commands=[\"/opt/tailorcards/bin/deploy.sh s3://$ARTIFACTS_BUCKET/api/$VERSION.jar $VERSION\"]" \
  --query Command.CommandId --output text)
status=Pending
for _ in $(seq 1 120); do
  status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
    --query Status --output text 2>/dev/null || echo Pending)
  case "$status" in Pending|InProgress|Delayed) sleep 5 ;; *) break ;; esac
done
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
  --query '[StandardOutputContent, StandardErrorContent]' --output text || true
echo "Deploy status: $status"
test "$status" = "Success"

echo "== Publishing the frontend"
(
  cd frontend
  npm ci
  VITE_API_URL="$API_URL" npm run build
  # Hashed assets never change: cache for a year. Everything else must be revalidated.
  aws s3 sync dist "s3://$FRONTEND_BUCKET" --delete --only-show-errors \
    --exclude "*" --include "assets/*" --cache-control "public, max-age=31536000, immutable"
  aws s3 sync dist "s3://$FRONTEND_BUCKET" --delete --only-show-errors \
    --exclude "assets/*" --cache-control "no-cache"
)
aws cloudfront create-invalidation --distribution-id "$FRONTEND_DISTRIBUTION_ID" --paths "/*" \
  --query Invalidation.Id --output text

echo "== Smoke test"
curl -fsS --retry 5 --retry-delay 5 "$API_URL/api/products" > /dev/null
curl -fsSI "$API_URL/api/products" | grep -qi "content-security-policy"
echo "Staging is on $VERSION"
