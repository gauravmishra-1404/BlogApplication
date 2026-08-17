#!/usr/bin/env bash
# Builds the jar HERE (whatever machine is running this script - a GitHub Actions runner, or a
# developer's own machine) and stages a lean, runtime-only deploy bundle - Dockerfile.deploy
# (just copies the jar in, no compiling) + the built jar, renamed app.jar. Deliberately NOT the
# repo's own root Dockerfile's approach of compiling ON the target instance: a real Beanstalk
# deploy did exactly that and overwhelmed the target t3.micro (1GB RAM) badly enough that its
# health agent stopped responding entirely mid-build (confirmed via
# `aws elasticbeanstalk describe-instances-health` showing "No data received from the instance").
# Building here instead - on hardware that actually has the RAM for a Maven compile - and shipping
# only the already-built artifact fixes that at the root, not just via a longer timeout.
#
# Run this before `terraform apply` whenever the app itself has changed; beanstalk.tf picks up the
# resulting zip via its md5 hash, so a new build automatically becomes a new application version.
# The GitHub Actions deploy workflow (.github/workflows/deploy.yml) runs this exact script too -
# one definition of "how to build a deploy bundle", not two that could quietly drift apart.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
STAGE_DIR="$BUILD_DIR/stage"

echo "Building jar via Maven..."
(cd "$REPO_ROOT" && ./mvnw -q -DskipTests clean package)

JAR_PATH=$(find "$REPO_ROOT/target" -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name 'original-*.jar' | head -n1)
if [[ -z "$JAR_PATH" ]]; then
  echo "No jar found under $REPO_ROOT/target after build - aborting" >&2
  exit 1
fi

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"

cp "$SCRIPT_DIR/Dockerfile.deploy" "$STAGE_DIR/Dockerfile"
cp "$JAR_PATH" "$STAGE_DIR/app.jar"
mkdir -p "$STAGE_DIR/.ebextensions"
cp "$SCRIPT_DIR/ebextensions/"*.config "$STAGE_DIR/.ebextensions/"

rm -f "$BUILD_DIR/beanstalk-app.zip"
(cd "$STAGE_DIR" && zip -q -r "$BUILD_DIR/beanstalk-app.zip" .)

echo "Built $BUILD_DIR/beanstalk-app.zip ($(du -h "$BUILD_DIR/beanstalk-app.zip" | cut -f1))"
