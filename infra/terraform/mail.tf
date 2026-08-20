# Fixes a real, confirmed production bug: every notification email (verification, and every other
# type - they all go through the same email-worker Lambda and the same From address) was landing
# in spam. Root cause wasn't the email content - it was the sender identity: EmailWorkerHandler
# sent as `From: bodhsea@gmail.com` through SendGrid's API, with zero domain authentication (no
# SPF/DKIM/DMARC) behind it. Claiming a @gmail.com From-address while actually sending through a
# non-Google server is one of the strongest spam signals a receiving mail server can see - Gmail's
# own DMARC policy makes every other provider (including Gmail itself) distrust unauthenticated
# gmail.com senders specifically, independent of anything about the email's actual content.
#
# Now that this project owns bodhsea.in on Route53 (https.tf), this is fixable: authenticate the
# sending domain so receiving servers can cryptographically verify Bodh Sea itself authorized the
# mail, then switch the From-address off gmail.com onto the now-authenticated domain.
#
# No Terraform SendGrid provider in use here (this project's whole provider surface is AWS-only,
# see providers.tf) - so unlike the AWS resources elsewhere in this repo, the actual SendGrid
# "domain authentication" resource itself was created via one direct, one-time API call
# (POST /v3/whitelabel/domains, domain=bodhsea.in, subdomain=mail, automatic_security=true),
# not something Terraform manages/tracks. What IS Terraform-managed is the Route53 side: the exact
# CNAME targets that call returned, hardcoded below same as beanstalk_hosted_zone_id already is in
# https.tf - these are permanent per-domain-authentication values tied to this SendGrid account,
# not something that needs re-deriving unless the domain authentication is ever recreated.
resource "aws_route53_record" "sendgrid_mail_cname" {
  count   = local.https_enabled ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = "mail.${var.domain_name}"
  type    = "CNAME"
  ttl     = 300
  records = ["u111323256.wl007.sendgrid.net"]
}

resource "aws_route53_record" "sendgrid_dkim1" {
  count   = local.https_enabled ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = "s1._domainkey.${var.domain_name}"
  type    = "CNAME"
  ttl     = 300
  records = ["s1.domainkey.u111323256.wl007.sendgrid.net"]
}

resource "aws_route53_record" "sendgrid_dkim2" {
  count   = local.https_enabled ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = "s2._domainkey.${var.domain_name}"
  type    = "CNAME"
  ttl     = 300
  records = ["s2.domainkey.u111323256.wl007.sendgrid.net"]
}

# Not part of SendGrid's own domain-authentication flow (that only covers SPF+DKIM, via the CNAMEs
# above) - added separately since Gmail/Yahoo's bulk-sender requirements (Feb 2024) expect a DMARC
# record too, and it materially helps inbox placement beyond just SPF+DKIM passing. p=none (monitor
# only, take no action on a failed check) deliberately - this is the safe starting policy since
# there's no DMARC aggregate-report pipeline set up yet to watch for misconfiguration before ever
# tightening to quarantine/reject; a hard-fail policy adopted blind risks silently dropping real
# mail if something here is ever slightly off.
#
# rua deliberately points at a real, already-reachable mailbox, not var.sendgrid_from_email -
# bodhsea.in has no MX record (this project only ever sends mail, never receives it), so an
# address @bodhsea.in can't actually receive the aggregate reports DMARC would try to deliver here.
resource "aws_route53_record" "dmarc" {
  count   = local.https_enabled ? 1 : 0
  zone_id = aws_route53_zone.main[0].zone_id
  name    = "_dmarc.${var.domain_name}"
  type    = "TXT"
  ttl     = 300
  records = ["v=DMARC1; p=none; rua=mailto:coolmind1404@gmail.com"]
}
