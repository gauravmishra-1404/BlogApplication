# Reuses the account's default VPC/subnets rather than building a custom one. A hand-rolled
# multi-AZ VPC with separate public/private/isolated subnets and a NAT gateway is the "proper"
# enterprise pattern - real complexity (route tables, subnet planning, NAT cost) this app's
# current scale doesn't justify yet. The RDS instance still isn't publicly reachable (see
# rds.tf) - that's enforced by its own publicly_accessible=false flag plus a security group
# scoped to only the app's own instance, not by which subnet it happens to sit in. Can graduate
# to a real custom VPC later without touching anything that depends on this file directly.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}
