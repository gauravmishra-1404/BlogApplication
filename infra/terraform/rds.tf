# Postgres for the main Spring Boot app itself - replacing Render's managed Postgres (shared
# hosting, unclear backup/reliability guarantees, only 1 month free). db.t4g.micro + single-AZ
# + exactly 20GB storage deliberately stays inside AWS's 12-month RDS free tier (750 instance-
# hours/month, 20GB storage, 20GB backup storage - see the cost conversation this came out of).
# Multi-AZ is off for the same reason: a real reliability upgrade, but it doubles the DB's
# compute cost, and this app's current traffic doesn't justify that yet - flipping multi_az to
# true later is a single-line change, no migration/downtime needed to turn it on.
#
# This is a NEW, empty database - getting the actual production data (real users/posts/
# comments) from Render's Postgres into this one is a separate pg_dump/pg_restore step, done
# once this instance exists and is reachable. Render's own database keeps running untouched
# throughout, so there's a live fallback until the new one is verified.

resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db-subnets"
  subnet_ids = data.aws_subnets.default.ids
}

# Generated rather than asked for - one less secret that has to pass through chat or get
# invented by hand. Stored in Terraform state (already how every other secret in this stack
# works, e.g. sendgrid_api_key in variables.tf) - state stays local/untracked, never committed.
resource "random_password" "db_master" {
  length  = 24
  special = false # avoids characters that need extra escaping in a JDBC connection URL
}

# Only reachable from the app's own security group (aws_security_group.app_instance, defined in
# beanstalk.tf) and inapp-worker's Lambda security group (aws_security_group.lambda_inapp, defined
# in lambda.tf - the only one of the 3 notification workers that talks to Postgres) - never open
# to 0.0.0.0/0. Two independent layers on purpose: publicly_accessible=false below AND this
# security group - either alone would be enough, both together means one misconfiguration doesn't
# fully expose the database.
resource "aws_security_group" "db" {
  name = "${var.project}-db"
  # NOTE: this description is now stale (it now also allows inapp-worker's Lambda SG, see the
  # ingress rules below) - deliberately NOT updated. AWS security group descriptions are
  # immutable; editing this string forces a full destroy+recreate of the security group, which
  # would mean detaching/reattaching it from a live, deletion_protection=true RDS instance for
  # zero functional benefit. The ingress rules themselves (the actual access control) update
  # in-place fine - only the free-text description doesn't.
  description = "Allows Postgres access only from the app EC2 instance"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Postgres from the app instance"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app_instance.id]
  }

  ingress {
    description     = "Postgres from inapp-worker Lambda"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda_inapp.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "main" {
  identifier                  = "${var.project}-postgres"
  engine                      = "postgres"
  engine_version              = "18.4" # matches Render's actual server version exactly - dumps can't restore into an OLDER major version
  allow_major_version_upgrade = true
  instance_class              = "db.t4g.micro"

  allocated_storage     = 20
  max_allocated_storage = 20 # storage autoscaling deliberately off - staying inside the free tier's 20GB cap on purpose, not by accident
  storage_type          = "gp3"

  db_name  = "bodhsea"
  username = "bodhseaadmin"
  password = random_password.db_master.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  multi_az                  = false
  backup_retention_period   = 7
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project}-postgres-final"

  deletion_protection = true

  # Without this, RDS queues changes (like the engine_version bump above) for the next
  # maintenance window instead of applying them now - fine for routine tuning, not what you
  # want mid-migration waiting on a version bump to actually take effect.
  apply_immediately = true
}
