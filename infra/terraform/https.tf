# Real domain + HTTPS for the main app - fixes a real, confirmed production bug: mobile Chrome
# (and increasingly browsers generally) force-upgrades navigation to https:// by default, and
# this environment had nothing listening on port 443 at all (confirmed via a direct TLS
# handshake attempt timing out, vs. HTTP on the same host responding in ~0.3s) - so any mobile
# visitor typing/tapping the bare elasticbeanstalk.com URL got ERR_CONNECTION_ABORTED. Desktop
# testing this whole build masked it, since we were always typing http:// explicitly.
#
# Everything in this file - and the aws:elbv2:listener:443 setting over in beanstalk.tf - is
# gated on var.domain_name being set, so it's a zero-diff no-op until the domain is actually
# purchased (see variables.tf's own comment on why: ACM won't issue a cert for someone else's
# *.elasticbeanstalk.com domain, so a real domain is the one piece nothing here can automate).

locals {
  https_enabled = var.domain_name != ""
}

# The zone Route53 will actually serve DNS from. If the domain wasn't bought through Route53
# itself, the registrar's nameservers need pointing at this zone's own NS records
# (`terraform output route53_name_servers`) before anything below actually resolves - Terraform
# can create the zone, but can't reach into a third-party registrar's account to repoint NS
# records for you.
resource "aws_route53_zone" "main" {
  count = local.https_enabled ? 1 : 0
  name  = var.domain_name
}

# Covers both the bare domain and www - whichever one someone actually types/links to, they get
# the same cert. DNS validation (not email validation) so Terraform can prove ownership itself
# via the zone above, no manual click-through needed.
resource "aws_acm_certificate" "main" {
  count                     = local.https_enabled ? 1 : 0
  domain_name               = var.domain_name
  subject_alternative_names = ["www.${var.domain_name}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = local.https_enabled ? {
    for dvo in aws_acm_certificate.main[0].domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  } : {}

  zone_id         = aws_route53_zone.main[0].zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.value]
  ttl             = 60
  allow_overwrite = true
}

# Blocks here until the validation records above actually resolve and ACM confirms them - the
# certificate isn't usable by the ALB listener (beanstalk.tf) until this completes.
resource "aws_acm_certificate_validation" "main" {
  count                   = local.https_enabled ? 1 : 0
  certificate_arn         = aws_acm_certificate.main[0].arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}

# NOT the same as an ALB's own hosted zone id (data.aws_lb_hosted_zone_id) - confirmed live, AWS
# rejected an ALIAS record built with that value ("the alias target name does not lie within the
# target zone"). A Beanstalk environment's *.elasticbeanstalk.com CNAME is a separate abstraction
# Beanstalk itself manages (not a direct alias to the underlying ALB it creates in LoadBalanced
# mode), with its own fixed hosted zone id per region, published in AWS's own docs:
# https://docs.aws.amazon.com/general/latest/gr/elasticbeanstalk.html - Z18NTBI3Y7N9TZ is
# specifically ap-south-1's; hardcoded rather than derived since this project runs in one region
# and a real region change would need re-checking this table anyway.
locals {
  beanstalk_hosted_zone_id = "Z18NTBI3Y7N9TZ"
}

# Points both the apex domain and www at Beanstalk's own environment CNAME, which itself already
# resolves through to the ALB once EnvironmentType=LoadBalanced (see beanstalk.tf). ALIAS (not a
# plain CNAME) specifically because the apex/root domain can't carry a CNAME record at all per
# DNS spec - Route53's ALIAS extension is what makes pointing the bare domain at an AWS resource
# possible in the first place.
resource "aws_route53_record" "app_apex" {
  count   = local.https_enabled ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_elastic_beanstalk_environment.lb[0].cname
    zone_id                = local.beanstalk_hosted_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "app_www" {
  count   = local.https_enabled ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = "www.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_elastic_beanstalk_environment.lb[0].cname
    zone_id                = local.beanstalk_hosted_zone_id
    evaluate_target_health = true
  }
}
