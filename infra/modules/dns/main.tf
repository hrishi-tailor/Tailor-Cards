# A hosted zone for the environment's own subdomain (e.g. staging.tailorcards.com). The parent domain stays where it
# is today: add the four NS records from the output at the current DNS provider to delegate the subdomain here.
resource "aws_route53_zone" "this" {
  name    = var.domain
  comment = "Tailor Cards ${var.domain} (delegated from the parent domain's DNS)"
}
