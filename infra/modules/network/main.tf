# VPC with public subnets (the app server, reachable on 80/443) and private subnets (the database, no route to the
# internet). No NAT Gateway (~$33/month): the app server is in a public subnet with its own address, and private
# resources only need AWS services, reached through VPC endpoints.

resource "aws_vpc" "this" {
  cidr_block           = var.cidr_block
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = var.name }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = var.name }
}

resource "aws_subnet" "public" {
  count                   = length(var.azs)
  vpc_id                  = aws_vpc.this.id
  availability_zone       = var.azs[count.index]
  cidr_block              = cidrsubnet(var.cidr_block, 8, count.index)
  map_public_ip_on_launch = false # the app server gets an Elastic IP instead
  tags                    = { Name = "${var.name}-public-${var.azs[count.index]}", Tier = "public" }
}

resource "aws_subnet" "private" {
  count             = length(var.azs)
  vpc_id            = aws_vpc.this.id
  availability_zone = var.azs[count.index]
  cidr_block        = cidrsubnet(var.cidr_block, 8, 100 + count.index)
  tags              = { Name = "${var.name}-private-${var.azs[count.index]}", Tier = "private" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private subnets have no default route: nothing in them can reach or be reached from the internet
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-private" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# S3 gateway endpoint: free, and keeps S3 traffic (deploy artifacts, images) on the AWS network
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id, aws_route_table.private.id]
  tags              = { Name = "${var.name}-s3" }
}

# Interface endpoints (SSM, Secrets Manager, CloudWatch Logs) let instances in private subnets use AWS without a
# NAT. They cost about $7.30 a month each per AZ, so they're off by default: the app server is in a public subnet
# and reaches these services over HTTPS directly.
resource "aws_security_group" "endpoints" {
  count       = var.enable_interface_endpoints ? 1 : 0
  name        = "${var.name}-endpoints"
  description = "HTTPS from inside the VPC to AWS interface endpoints"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTPS from the VPC"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.cidr_block]
  }
  tags = { Name = "${var.name}-endpoints" }
}

resource "aws_vpc_endpoint" "interface" {
  for_each            = var.enable_interface_endpoints ? toset(["ssm", "ssmmessages", "ec2messages", "secretsmanager", "logs"]) : toset([])
  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = [aws_subnet.private[0].id]
  security_group_ids  = [aws_security_group.endpoints[0].id]
  tags                = { Name = "${var.name}-${each.key}" }
}

# The default security group allows nothing (resources always get their own groups)
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-default-deny" }
}

# Flow logs of rejected traffic only: shows scans and misconfigured rules, at a fraction of the volume of ALL
resource "aws_flow_log" "rejected" {
  count                = var.enable_flow_logs ? 1 : 0
  vpc_id               = aws_vpc.this.id
  traffic_type         = "REJECT"
  log_destination_type = "cloud-watch-logs"
  log_destination      = var.flow_log_group_arn
  iam_role_arn         = var.flow_log_role_arn
}
