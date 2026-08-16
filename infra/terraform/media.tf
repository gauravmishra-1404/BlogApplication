# Post media (images/video attached to a post) - S3 for storage. CDN was originally meant to be
# CloudFront, but this AWS account is new enough that AWS Support has explicitly refused to
# enable CloudFront on it ("no purchase history" - a fraud-prevention gate on new accounts using
# only promotional credits, not something waiting or re-asking fixes). The plan is Cloudflare
# instead (not managed by this Terraform config, no AWS provider covers it) sitting in front of
# this same bucket - but that needs a domain name (bodhsea.in) which hasn't been bought yet
# (deferred by choice, not blocked). Until then, AWS_MEDIA_CDN_DOMAIN on Render just points
# directly at this bucket's own public endpoint (see outputs.tf's media_bucket_regional_domain_name)
# - no CDN caching yet, but fully functional, and swapping in Cloudflare later is a one-env-var
# change, nothing in the app or this file needs to change again.
#
# Either way the bucket can't stay fully private behind Origin Access Control the way the
# original CloudFront design had it - a plain public S3 endpoint (and Cloudflare, later) both
# fetch origin content the same way any HTTP client would, no AWS-specific signing available.
# This is an acceptable trade, not a compromise: post media is public content by definition
# (anyone reading the post can already see it), so public-read scoped tightly to the posts/*
# prefix is exactly as secure as OAC was for this specific case. The app still never proxies file
# bytes through itself - it only generates short-lived presigned PUT URLs (see
# services/MediaUploadService), and the browser uploads straight to S3 with those.

resource "aws_s3_bucket" "post_media" {
  bucket = "${var.project}-post-media-${data.aws_caller_identity.current.account_id}"
}

# ACLs stay blocked (nothing in this app ever sets an object ACL - uploads go through presigned
# PUT with no ACL header). Bucket *policy* public access is deliberately allowed, scoped by the
# policy below to posts/* GetObject only - nothing else in the bucket becomes public, and nothing
# can be made public by an ACL as a side channel.
resource "aws_s3_bucket_public_access_block" "post_media" {
  bucket                  = aws_s3_bucket.post_media.id
  block_public_acls       = true
  block_public_policy     = false
  ignore_public_acls      = true
  restrict_public_buckets = false
}

# CORS is required because the upload itself is cross-origin - the browser is on
# blogapplication-2ncl.onrender.com (or localhost while testing), PUTting directly to an S3
# bucket domain. Without this, the browser's own CORS preflight blocks the upload before it
# ever reaches S3, regardless of how correct the presigned URL/signature is. GET doesn't need a
# CORS rule here - <img>/<video> tags aren't subject to CORS the way a PUT is, and the byte
# fetch for those happens Cloudflare-to-S3 (server-to-server), not browser-to-S3.
resource "aws_s3_bucket_cors_configuration" "post_media" {
  bucket = aws_s3_bucket.post_media.id

  cors_rule {
    allowed_methods = ["PUT"]
    # Beanstalk's origin (the new live app) plus var.app_base_url (Render's - kept until Render's
    # web service is actually decommissioned, so an upload from whichever one is currently
    # fronting real traffic isn't CORS-blocked mid-transition) plus localhost for local testing.
    allowed_origins = [local.beanstalk_app_base_url, var.app_base_url, "http://localhost:8080"]
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

# Anyone can GET anything under posts/* - deliberate, see the file-level comment above. Nothing
# else in the bucket is covered by this statement, and PutObject is still locked down to the
# app's own IAM identity only (aws_iam_group_policy.app_media_upload below) - this policy only
# ever grants read.
resource "aws_s3_bucket_policy" "post_media_public_read" {
  bucket = aws_s3_bucket.post_media.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadPostMedia"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.post_media.arn}/posts/*"
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.post_media]
}

# Same app identity (bodhsea-notification-service) as the notification system - one Spring Boot
# process, one AWS credential pair already configured on Render (AWS_ACCESS_KEY_ID/SECRET), just
# a second scoped policy attached to the same group rather than a whole separate IAM user/second
# credential pair to manage for no real benefit. Presigning a PUT URL is a local signing
# operation (no network call), but the resulting URL's authorization is based on THIS identity's
# own permissions - it needs real s3:PutObject on the prefix posts/* to generate a URL that
# actually works when the browser uses it.
resource "aws_iam_group_policy" "app_media_upload" {
  name  = "${var.project}-media-upload"
  group = aws_iam_group.app_group.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject"]
      Resource = "${aws_s3_bucket.post_media.arn}/posts/*"
    }]
  })
}
