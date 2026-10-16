output "zone_id" {
  value = aws_route53_zone.this.zone_id
  # Records in the zone (certificate validation above all) wait for the CAA record: ACM checks CAA when it issues
  depends_on = [aws_route53_record.caa]
}

output "name_servers" {
  description = "Add these as NS records for the subdomain at the parent domain's DNS provider"
  value       = aws_route53_zone.this.name_servers
}
