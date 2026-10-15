output "zone_id" {
  value = aws_route53_zone.this.zone_id
}

output "name_servers" {
  description = "Add these as NS records for the subdomain at the parent domain's DNS provider"
  value       = aws_route53_zone.this.name_servers
}
