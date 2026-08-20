# Shared session store (ElastiCache Redis) - fixes a real, silent scaling gap: sessions were
# plain in-memory HttpSession + an in-memory Spring Security SessionRegistry
# (security/SecurityConfig.java), both scoped to whichever single JVM handled the request. The
# Beanstalk ASG (beanstalk.tf) is already configured to autoscale up to 4 instances, but was
# sitting at desired capacity 1 the whole time this was true - meaning the moment it ever scaled
# past 1 instance under real load, the ALB would round-robin requests across instances with no
# session affinity (no stickiness configured either) and users would get logged out/bounced
# between "logged in" and "logged out" at random, depending which instance handled each request.
# Moving sessions into a store every instance shares fixes that regardless of how many instances
# are actually running.
#
# cache.t3.micro, single node - AWS's ElastiCache free tier covers 750 node-hours/month of
# cache.t3.micro or cache.t2.micro for the first 12 months, the same free-tier shape this
# project already leans on for EC2 (Beanstalk) and RDS. No replica/Multi-AZ - a cache restart
# just means everyone has to log in again (annoying, not data loss - nothing here is a system of
# record), so the extra cost/complexity of a standby node isn't worth it yet, same reasoning
# rds.tf's own comment gives for leaving multi_az off there.
#
# No AUTH token / TLS in transit - deliberately matching the DB connection's own existing
# security posture (VPC + security-group isolation only, see aws_security_group.db in rds.tf),
# not introducing a stricter bar for this new resource than the one already accepted for
# Postgres. The security group below is the actual access control: only the app instance's own
# security group can reach port 6379 at all.

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project}-redis-subnets"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_security_group" "redis" {
  name        = "${var.project}-redis"
  description = "Allows Redis access only from the app EC2 instance"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Redis from the app instance"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app_instance.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Explicit rather than relying on default.redis7's own implicit maxmemory-policy - the choice
# should be visible and intentional here, not an external AWS default a future reader of this
# file has no way to see.
#
# volatile-lru: every key this cluster will ever hold is a session (nothing else uses this
# instance), and Spring Session sets a TTL on every one of them matching
# server.servlet.session.timeout (Spring Boot's default: 30 minutes) - so every key here is
# already eviction-eligible, nothing needs protecting from eviction. Under memory pressure, this
# evicts whichever session has gone longest without being touched first, keeping actively-used
# sessions alive as long as possible. The alternative that actually matters to rule out is
# Redis's OWN default, noeviction: once full, it rejects new writes outright instead of evicting
# anything - meaning new logins would start failing the moment the cache fills, a much worse
# failure mode than quietly signing out whoever's already been idle longest.
resource "aws_elasticache_parameter_group" "sessions" {
  name   = "${var.project}-sessions"
  family = "redis7"

  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"
  }
}

resource "aws_elasticache_cluster" "sessions" {
  cluster_id           = "${var.project}-sessions"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.sessions.name

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  apply_immediately = true
}
