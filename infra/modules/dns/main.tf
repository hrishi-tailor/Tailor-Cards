# A hosted zone for the environment's own subdomain (e.g. staging.tailorcards.com). The parent domain stays where it
# is today: add the four NS records from the output at the current DNS provider to delegate the subdomain here.
resource "aws_route53_zone" "this" {
  name    = var.domain
  comment = "Tailor Cards ${var.domain} (delegated from the parent domain's DNS)"
}

# Which certificate authorities may issue for this subdomain. The parent domain's CAA records (set by its DNS host)
# don't include Amazon, so without this ACM refuses to issue the CloudFront certificates (CAA_ERROR). A CAA record
# here overrides the parent's for the subdomain and everything under it: ACM for the site and images, Let's Encrypt
# for the API server.
resource "aws_route53_record" "caa" {
  zone_id = aws_route53_zone.this.zone_id
  name    = var.domain
  type    = "CAA"
  ttl     = 300
  records = [
    "0 issue \"amazon.com\"",
    "0 issue \"amazontrust.com\"",
    "0 issue \"awstrust.com\"",
    "0 issue \"amazonaws.com\"",
    "0 issue \"letsencrypt.org\"",
  ]
}
