variable "aws_region" {
  description = "AWS region for every resource this stack creates."
  type        = string
  default     = "ap-south-1" # Mumbai
}

variable "project" {
  description = "Prefix applied to every resource name, so this stack's resources are easy to find/tear down as a group."
  type        = string
  default     = "bodhsea-notifications"
}

# --- Secrets the Lambda workers need at runtime. Set these via TF_VAR_ environment variables
# (e.g. TF_VAR_sendgrid_api_key=... terraform apply) or a git-ignored terraform.tfvars file -
# never commit real values. Terraform stores them in state either way (a general Terraform
# limitation, not specific to this project) - state should stay local or in an encrypted
# backend, not committed to git. ---

variable "sendgrid_api_key" {
  description = "Same SendGrid key already used by the main app's SendGridEmailService - the email-worker Lambda calls the same API directly."
  type        = string
  sensitive   = true
}

variable "sendgrid_from_email" {
  description = "Verified SendGrid single-sender address."
  type        = string
  default     = "bodhsea@gmail.com"
}

# Not a secret - just needs to match the main app's own app.base-url (application.properties)
# so email-worker's HTML emails link back to the real deployed site. Only email-worker uses
# this (to turn a notification's relative targetUrl, e.g. "/profile/x", into an absolute link a
# mail client can follow) - push/inapp have no equivalent need for it.
variable "app_base_url" {
  description = "The deployed app's own base URL, for turning a notification's relative targetUrl into an absolute link inside emails."
  type        = string
  default     = "https://blogapplication-2ncl.onrender.com"
}

# Empty by default on purpose - everything HTTPS-related in https.tf and beanstalk.tf's listener
# settings is gated on `var.domain_name != ""`, so this whole feature stays a zero-diff no-op
# (EnvironmentType stays SingleInstance, no Route53 zone, no ACM cert) until a real domain is set.
# ACM/any CA won't issue a certificate for someone else's *.elasticbeanstalk.com domain - a real
# domain is the one piece that can't be worked around, which is why this was deferred until now
# and is the one manual step (buying it, then setting this var) nothing here can automate.
#
# Once purchased: set via TF_VAR_domain_name=yourdomain.tld or terraform.tfvars, matching the
# existing convention for sendgrid_api_key etc. If the registrar isn't Route53 itself, point the
# domain's nameservers at the values `terraform output route53_name_servers` prints after the
# first apply with this set - that's what actually makes DNS (and ACM's validation records)
# resolve.
variable "domain_name" {
  description = "The real domain for this app (e.g. bodhsea.in) - enables HTTPS (ALB + ACM + Route53) once set. Leave blank until the domain is actually purchased."
  type        = string
  default     = ""
}

# A public, community-maintained Lambda Layer bundling a static ffmpeg binary at /opt/bin/ffmpeg
# (the conventional extraction path every Lambda Layer publishes under) - transcode-worker
# shells out to it via ProcessBuilder rather than this project building/publishing its own layer.
# Region- and architecture-specific (this stack's Lambdas are x86_64/java21, region var.aws_region
# - ap-south-1 by default), so the actual ARN has to be looked up/verified for that exact
# region+architecture before ever running `terraform apply` with this set - a stale or
# wrong-region ARN fails at deploy time (layer not found), not silently.
variable "ffmpeg_layer_arn" {
  description = "ARN of a public Lambda Layer providing a static ffmpeg binary at /opt/bin/ffmpeg, for the transcode-worker Lambda. Verify this against the actual target region/architecture before applying - left blank by default since no working default exists across regions."
  type        = string
  default     = ""
}

variable "fcm_service_account_json" {
  # FCM's old "server key" HTTP API was shut down by Google in June 2024 - the only API left is
  # HTTP v1, which authenticates via a Firebase service account (a JSON key file you download
  # from Firebase Console > Project Settings > Service Accounts > Generate new private key),
  # not a simple bearer token. Paste that file's full JSON content here as a string (minified
  # is fine, typically ~2KB - comfortably under Lambda's 4KB total env-var limit for this one
  # function). push-worker no-ops (logs and skips, doesn't fail/retry) if left blank, same
  # "missing config logs, never crashes" tolerance the main app already uses for Cloudinary.
  description = "Full Firebase service account JSON (as a string), or blank to leave push notifications disabled for now."
  type        = string
  sensitive   = true
  default     = ""
}
