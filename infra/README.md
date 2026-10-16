# Tailor Cards on AWS (staging)

Terraform for a **staging copy** of Tailor Cards on AWS. It is completely separate from production, which stays on Render (API and database), Vercel (frontend) and Supabase (images). Nothing here touches production.

```
                      staging.tailorcards.com          images.staging.tailorcards.com
                               │                                    │
  browser ──HTTPS──► CloudFront (ACM cert) ──► S3: React build       CloudFront ──► S3: images
     │
     └──HTTPS──► api.staging.tailorcards.com (Route 53 → Elastic IP)
                    │
            ┌───────┴─────────── VPC 10.20.0.0/16 ─────────────────────────────┐
            │ public subnet                         private subnets (2 AZs)     │
            │  EC2 t4g.micro (ARM)                   RDS PostgreSQL 17           │
            │   Nginx :443 (Let's Encrypt) ───► :8080 Spring Boot ──► :5432       │
            │   no SSH: SSM Session Manager           encrypted, daily backups   │
            │   ──► Secrets Manager, SES, S3 (gateway endpoint), CloudWatch       │
            └────────────────────────────────────────────────────────────────────┘
  push to main ──► CodeBuild (GitHub connection, IAM role) ──► S3 (jar, frontend), SSM Run Command, CloudFront invalidation
```

## Layout

| Path | What it is |
| :--- | :--- |
| `bootstrap/` | One-time: the S3 bucket that holds Terraform state for every environment (versioned, encrypted, S3-native locking) |
| `envs/staging/` | The staging environment: small sizes, no deletion protection, $10 budget alert |
| `envs/prod/` | The same stack with production sizing (Multi-AZ, deletion protection, 14-day backups). **Not applied**; kept so the two stay identical in shape |
| `modules/stack/` | Wires one environment together from the modules below |
| `modules/network/` | VPC, public and private subnets, internet gateway, S3 gateway endpoint, optional interface endpoints, flow logs |
| `modules/app_server/` | EC2 instance, security group, IAM role, Elastic IP, DNS record, deploy-artifacts bucket, boot script (`templates/cloud-init.yaml.tftpl`) |
| `modules/database/` | RDS PostgreSQL, subnet group, security group, parameter group, the database secret |
| `modules/cdn_site/` | Private S3 bucket + CloudFront + ACM certificate + DNS. Used twice: frontend and images |
| `modules/dns/` | Route 53 hosted zone for the staging subdomain |
| `modules/email/` | SES domain identity with DKIM, SPF and DMARC records |
| `modules/secrets/` | The app's Secrets Manager secret (values set outside Terraform) |
| `modules/monitoring/` | Log groups, alarms, SNS email alerts, monthly budget |
| `modules/codebuild_deploy/` | CodeBuild project, GitHub connection, push webhook and its least-privilege role: tests and deploys on every push to `main` (on by default) |
| `modules/github_oidc/` | GitHub OIDC provider and deploy role for GitHub Actions. Off by default (`enable_github_oidc`): see [Region and account](#region-and-account) |
| `modules/scheduler/` | Optional: stop the server and database nights and weekends |

## Region and account

The defaults target **us-east-2 (Ohio)**, the region of the AWS account this was planned against: a new-experience account on the **Free plan with $100 in credits**. Region is a variable (`region`, `azs`) if you use another.

- **CloudFront certificates still go in us-east-1.** CloudFront only accepts ACM certificates from there, so a second provider (`aws.us_east_1`) creates those two certificates. Everything else is in `region`.
- **Deploys come from CodeBuild, not GitHub Actions.** The account's service control policy explicitly denies `iam:*Provider*`, so GitHub's OIDC provider can't be created. CodeBuild runs as an IAM role, so there are still no stored AWS keys anywhere. It clones the public repository itself and deploys when started (`aws codebuild start-build`). A CodeConnections GitHub connection with a deploy-on-push webhook is built in (`use_github_connection = true`), but on this account CodeBuild rejected it at project creation ("User is not authorized to access connection") even with an Available connection and a role allowed to use it, so it's off. On an account that allows OIDC providers, set `enable_github_oidc = true` to use `deploy-staging.yml` instead; both paths run the same `infra/scripts/deploy-staging.sh`.
- **Checked against the account.** `terraform plan` ran read-only against it in us-east-2: **104 to add, 0 to change, 0 to destroy**. The AMI resolved (Amazon Linux 2023 ARM), `t4g.micro` is offered in us-east-2a/b/c, and RDS offers PostgreSQL 17 (17.9) on `db.t4g.micro`. All the services used (EC2, RDS, S3, CloudFront, ACM, Route 53, SES, Secrets Manager, SSM, EventBridge Scheduler, CloudWatch, SNS, Budgets, CodeBuild, CodeConnections) are allowed by the account's policies.
- **How long $100 lasts.** About 3½ months always on, or about 6 with `enable_scheduler = true`. The budget counts usage **before credits** (otherwise it would read $0 and never alert), so its emails track how fast the credits go. Free-plan accounts close once the credits or the plan period run out unless you upgrade to a paid plan in the Billing console; upgrade before then if staging should stay up.

## Every resource, and what it costs

These are on-demand prices in us-east-2 (the same as us-east-1 for everything here), assuming 730 hours a month. They're from AWS's published pricing as I know it, so check them in the [AWS Pricing Calculator](https://calculator.aws/) before you rely on them. The plan creates **104 resources**; most (IAM, security groups, route tables, DNS records, policies) cost nothing.

| Resource | Why it's there | Monthly |
| :--- | :--- | ---: |
| EC2 `t4g.micro` (2 ARM vCPU burst, 1 GiB + 2 GiB swap) | Runs the jar behind Nginx | $6.13 |
| EBS gp3 root volume, 12 GB, encrypted | OS, app, logs | $0.96 |
| Elastic IP (public IPv4) | Fixed address for `api.` and HTTPS (AWS charges $0.005/hour for every public IPv4) | $3.65 |
| RDS PostgreSQL `db.t4g.micro`, single-AZ | The database, in private subnets | $11.68 |
| RDS gp3 storage 20 GB (autoscaling to 50), encrypted | Data | $2.30 |
| RDS automated backups, 1 day (Free-plan accounts reject 7; raise it after upgrading) | Point-in-time restore | $0 (free up to the database's size) |
| S3: frontend, images, deploy artifacts | Static files and jars | ~$0.10 |
| CloudFront × 2, ACM certificates × 2 | HTTPS CDN for the site and images | $0 (always-free tier: 1 TB and 10 M requests a month; certificates are free) |
| Route 53 hosted zone | `staging.tailorcards.com` DNS | $0.50 |
| Secrets Manager × 2 (app, database) | Secrets, never on disk or in Terraform state | $0.80 |
| SES | Sign-in codes and notifications | ~$0.01 ($0.10 per 1,000 emails) |
| CloudWatch: 4 log groups (30 days), 7 alarms, agent metrics, metric filters | Logs, alarms | ~$0.30 (alarms and metrics within the free tier) |
| VPC flow logs (rejected traffic only) | See scans and blocked connections | ~$0.10 |
| CodeBuild, ARM small, ~8 minutes a deploy | Test and deploy on every push to `main` | ~$0.03 a deploy ($0.0034/minute; the first 100 minutes a month are free on the free tier) |
| CodeConnections GitHub connection | Lets CodeBuild read the repository | $0 |
| S3 gateway endpoint, SNS email, AWS Budgets (first 2), SSM Session Manager, IAM, KMS AWS-managed keys | | $0 |
| Data out to the internet | First 100 GB a month free | $0 |
| **Total** | | **≈ $26.50** |

**The budget is $30** (it was $10, which the RDS instance and public IPv4 address alone exceed). It emails at 80% of actual spend and when the forecast passes 100%, counting usage before credits. Ways to bring the cost down:

| Option | Monthly | Trade-off |
| :--- | ---: | :--- |
| As configured | ≈ $26.50 | Always on |
| `enable_scheduler = true` (running 8am–8pm Toronto time, weekdays) | ≈ $15 | Staging is off nights and weekends; deploys then fail until morning |
| Credits | $0 out of pocket | The $100 in credits on this account cover about 3½ months as configured |
| PostgreSQL on the EC2 instance instead of RDS | ≈ $12 | Loses managed backups and point-in-time restore. Not done, since you asked for RDS |

**Optional costs that are off by default:**
- Interface VPC endpoints (`enable_interface_endpoints`): $7.30 a month each per AZ, 5 of them. Not needed here, because the server is in a public subnet and reaches AWS APIs over HTTPS.
- WAF: $5 or more a month.
- EC2 detailed monitoring: $2 a month.
- Multi-AZ RDS: doubles the database cost.

## Apply

**You need:** an AWS account, an admin login for it through `aws configure sso` (recommended) or an access key, the AWS CLI v2, and Terraform 1.11 or newer.

Use the profile for the account: `export AWS_PROFILE=tailorcards` (sign in with `aws login --profile tailorcards` when it expires).

**1. State bucket (once per account).**
```bash
cd infra/bootstrap
terraform init
terraform apply                    # creates tailorcards-terraform-state-<account-id>
```

**2. Configure staging.**
```bash
cd ../envs/staging
cp backend.hcl.example backend.hcl                 # put the bucket name from step 1
cp terraform.tfvars.example terraform.tfvars       # domain, account id, alert email
terraform init -backend-config=backend.hcl
```

**3. Create the DNS zone first, then delegate it.** The certificates can only validate once the subdomain points at Route 53.
```bash
terraform apply -target=module.stack.module.dns
terraform output name_servers
```
At the DNS provider for `tailorcards.com` (Vercel today: Domains > tailorcards.com > DNS Records), add four `NS` records named `staging`, one per name server. Check that it works: `dig NS staging.tailorcards.com +short`

With `use_github_connection = true`, also target `'module.stack.module.codebuild_deploy[0].aws_codeconnections_connection.github[0]'` here and authorise it in the console (Developer Tools > Settings > Connections > Update pending connection) before step 4.

**4. Review the plan, then apply it.**
```bash
terraform plan -out=staging.tfplan
terraform apply staging.tfplan
```
This takes about 15 minutes; RDS and the CloudFront certificates take the longest.

**5. Set the app's secrets.** Terraform never sees these values.
```bash
cat > /tmp/app-secret.json <<'JSON'
{
  "ADMIN_PASSWORD": "<a new strong password>",
  "TOTP_ENCRYPTION_KEY": "<openssl rand -base64 32>",
  "CHATBOT_ENABLED": "true",
  "ANTHROPIC_API_KEY": "<staging key>",
  "RAPIDAPI_KEY": "<key>",
  "STRIPE_SECRET_KEY": "<sk_test_... a TEST key, never live>",
  "STRIPE_WEBHOOK_SECRET": "<whsec_...>"
}
JSON
aws secretsmanager put-secret-value --secret-id tailorcards-staging/app --secret-string file:///tmp/app-secret.json
rm /tmp/app-secret.json
```

**6. Confirm the alerts email** (SNS sends a confirmation link). Then in the SES console, **request production access**. Until then, SES only delivers to addresses you've verified.

**7. First deploy.** The server has no app until the first deploy. Start one now, or push to `main`:
```bash
aws codebuild start-build --project-name "$(terraform output -raw deploy_project)"
```
Follow it in the console (CodeBuild > Build projects > `tailorcards-staging-deploy`) or in the `/tailorcards-staging/deploy` log group. Then open `terraform output site_url`.

Run the same command to deploy again after merging to `main`. (With `use_github_connection = true`, every push to `main` deploys automatically; `enable_deploy_webhook = false` pauses that.)

## Destroy

```bash
cd infra/envs/staging
terraform plan -destroy -out=destroy.tfplan
terraform apply destroy.tfplan
```

RDS takes a final snapshot (`tailorcards-staging-final`) on the way out, and it's billed as storage until you delete it:
```bash
aws rds delete-db-snapshot --db-snapshot-identifier tailorcards-staging-final
```

The state bucket in `bootstrap/` is protected (`prevent_destroy`). Remove it last, by hand, once no environment uses it. Also remove the `NS` record for `staging` at your DNS provider.

## CI/CD

| Workflow | Does | AWS access |
| :--- | :--- | :--- |
| `infra.yml` | `terraform fmt -check`, `validate` (bootstrap, staging, prod), TFLint with the AWS ruleset, Checkov | None |
| CodeBuild `tailorcards-staging-deploy` (`buildspec-staging.yml`) | On every push to `main`: builds and tests (`mvn verify`, with Docker for the PostgreSQL tests), then `infra/scripts/deploy-staging.sh`: uploads the jar, runs `deploy.sh` on the server through SSM Run Command (health check, rollback on failure), builds and syncs the frontend, invalidates CloudFront, smoke test. One build at a time | The project's IAM role; no stored keys |
| `deploy-staging.yml` | The same steps from GitHub Actions. Only for accounts that allow OIDC providers (`enable_github_oidc = true`); stays off until the repository variable `STAGING_DEPLOY_ENABLED = true` is set | GitHub OIDC: a short-lived token, no stored keys |

**How the deploy role is limited.** It can only put jars in the artifacts bucket, sync the frontend bucket, invalidate that one distribution, run `AWS-RunShellScript` on that one instance, write its own log group and use its own GitHub connection. The GitHub Actions role additionally trusts only tokens for `repo:hrishi-tailor/Tailor-Cards:environment:staging`.

**Checkov exceptions.** Checkov's accepted exceptions (mostly cost trade-offs for staging) are listed with reasons in `.checkov.yaml`.

## Linux runbook

**Connect to the server.** There's no SSH and no key; access goes through AWS SSM.
```bash
aws ssm start-session --target "$(terraform -chdir=infra/envs/staging output -raw instance_id)"
sudo -i        # the session starts as ssm-user; become root for the commands below
```

### Is the app running?
```bash
systemctl status tailorcards          # active (running)? since when? last log lines
systemctl status nginx
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/products   # 200 = the app answers
ss -ltnp | grep -E ':80|:443|:8080'   # what's listening on which port
free -m; df -h /                      # memory and swap; disk space
uptime                                # load averages (2 vCPUs: under 2 is fine)
```

### Read the logs (journalctl)
```bash
journalctl -u tailorcards -f                       # follow live (Ctrl+C to stop)
journalctl -u tailorcards --since "15 min ago"     # recent
journalctl -u tailorcards -p err --since today     # errors only
journalctl -u tailorcards -b                       # since the last boot
journalctl -u tailorcards | grep "Buylist timing"  # the app's timing lines
tail -f /var/log/nginx/access.log /var/log/nginx/error.log
journalctl -u nginx --since today
cat /var/log/cloud-init-output.log                 # what happened on first boot (packages, certbot)
```
The same app and Nginx logs are in CloudWatch Logs under `/tailorcards-staging/...`.

### Restart, deploy, roll back
```bash
sudo systemctl restart tailorcards              # restart the app (reads secrets again)
sudo systemctl reload nginx                     # reload Nginx config without dropping connections
sudo nginx -t                                   # check Nginx config before reloading

ls -l /opt/tailorcards/current.jar /opt/tailorcards/releases/   # which release is live; the last 5 are kept
sudo /opt/tailorcards/bin/deploy.sh s3://<artifacts-bucket>/api/<version>.jar <version>   # deploy by hand
# Roll back to an earlier release:
sudo ln -sfn /opt/tailorcards/releases/<older>.jar /opt/tailorcards/current.jar && sudo systemctl restart tailorcards
```

### Patch the OS (Amazon Linux 2023)
AL2023 has versioned repositories: updates come within the release you're on, and moving to a newer release is a deliberate step.
```bash
sudo dnf check-update                          # what's available
sudo dnf upgrade --security -y                 # security fixes only
sudo dnf check-release-update                  # is a newer AL2023 release out?
sudo dnf upgrade --releasever=latest -y        # move to the newest release (staging first)
sudo dnf needs-restarting -r || sudo reboot    # reboot if the kernel or core libraries changed
```
After a reboot, `tailorcards` and `nginx` start on their own; check with `systemctl status`.

### HTTPS certificate
```bash
sudo certbot certificates                      # domains and expiry date
sudo certbot renew --dry-run                   # test renewal (a timer renews automatically twice a day)
systemctl list-timers certbot-renew.timer
```

### Restore the database from a backup
RDS keeps automatic backups for 7 days and can restore to any second in that window. A restore always creates a **new** database instance; the current one is never overwritten.
```bash
# 1. Restore to a new instance at a point in time (UTC), in the same subnets and security group
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier tailorcards-staging \
  --target-db-instance-identifier tailorcards-staging-restore \
  --restore-time 2026-10-15T14:30:00Z \
  --db-subnet-group-name tailorcards-staging \
  --vpc-security-group-ids "$(aws ec2 describe-security-groups --filters Name=group-name,Values=tailorcards-staging-db --query 'SecurityGroups[0].GroupId' --output text)" \
  --db-parameter-group-name tailorcards-staging \
  --no-publicly-accessible
aws rds wait db-instance-available --db-instance-identifier tailorcards-staging-restore

# 2. Point the app at it: change "host" in the database secret, then restart the app
aws rds describe-db-instances --db-instance-identifier tailorcards-staging-restore \
  --query 'DBInstances[0].Endpoint.Address' --output text
#    (edit the host in Secrets Manager: tailorcards-staging/database)
#    on the server:  sudo systemctl restart tailorcards
```
**Restore from a snapshot** (for example the final snapshot after a destroy): use `aws rds restore-db-instance-from-db-snapshot` with the same options.

**Look inside the database from your laptop.** Use an SSM port forward through the app server; the database itself is never public.
```bash
aws ssm start-session --target <instance-id> --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<rds endpoint>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
# then, in another terminal (password from the database secret):
psql "host=localhost port=15432 dbname=tailorcards user=tailorcards sslmode=require"
```
For a logical backup you control, run `pg_dump` through the same tunnel.

## Hardening for prod

`envs/prod` already uses:
- Multi-AZ RDS with deletion protection and 14-day backups.
- A `t4g.small` server.
- Recoverable secrets and 90-day logs.

Before moving production to AWS, also consider:
- **More capacity:** an Application Load Balancer with two instances across AZs.
- **Edge protection and encryption keys:** AWS WAF with managed rules on CloudFront and the load balancer, and customer-managed KMS keys.
- **Logging:** CloudFront and S3 access logs, and GuardDuty.
- **DNSSEC** on the zone.
- **Patching:** AWS Backup for cross-region copies, and Systems Manager Patch Manager instead of manual patching.
