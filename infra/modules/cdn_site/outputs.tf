output "bucket" {
  value = aws_s3_bucket.site.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.site.arn
}

output "distribution_id" {
  value = aws_cloudfront_distribution.site.id
}

output "distribution_arn" {
  value = aws_cloudfront_distribution.site.arn
}

output "url" {
  value = "https://${var.domain}"
}
