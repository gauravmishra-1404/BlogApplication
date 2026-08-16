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
