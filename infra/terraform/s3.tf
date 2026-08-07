# Lambda deployment artifacts (the 3 fat jars built by `mvn package` under infra/lambdas/) go
# through S3 rather than a direct upload - push-worker's jar alone is ~58MB (the Firebase Admin
# SDK pulls in Firestore/Cloud Storage/Cloud Monitoring clients it never actually calls, as a
# side effect of being one monolithic SDK instead of per-product artifacts), well past Lambda's
# 50MB direct-upload ceiling. Routing every worker through S3 here (not just push-worker) keeps
# all 3 on the same code path, so a future dependency bump on email/inapp-worker that pushes
# either past 50MB doesn't quietly break `terraform apply` later.
data "aws_caller_identity" "current" {}

# Bucket names are globally unique across ALL of AWS, not just this account - suffixing with the
# account id (itself globally unique) guarantees no collision without needing a random provider
# or manual name-bikeshedding.
resource "aws_s3_bucket" "lambda_artifacts" {
  bucket = "${var.project}-lambda-artifacts-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "lambda_artifacts" {
  bucket                  = aws_s3_bucket.lambda_artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_object" "worker_jar" {
  for_each = toset(local.channels)

  bucket = aws_s3_bucket.lambda_artifacts.id
  key    = "${each.key}-worker-1.0.0.jar"
  source = local.worker_jar[each.key]
  etag   = filemd5(local.worker_jar[each.key])
}
