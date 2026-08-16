#!/usr/bin/env bash
# Stages exactly what Elastic Beanstalk's Docker platform needs to build the same image the
# repo's own Dockerfile already defines - Dockerfile, pom.xml, mvnw, .mvn, src - into a clean
# zip, deliberately excluding everything else (.git, target/, infra/, IDE files). Run this
# before `terraform apply` whenever the app itself has changed; beanstalk.tf picks up the
# resulting zip via its md5 hash, so a new build automatically becomes a new application version.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
STAGE_DIR="$BUILD_DIR/stage"

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"

cp "$REPO_ROOT/Dockerfile" "$STAGE_DIR/"
cp "$REPO_ROOT/pom.xml" "$STAGE_DIR/"
cp "$REPO_ROOT/mvnw" "$STAGE_DIR/"
cp -r "$REPO_ROOT/.mvn" "$STAGE_DIR/"
cp -r "$REPO_ROOT/src" "$STAGE_DIR/"

rm -f "$BUILD_DIR/beanstalk-app.zip"
(cd "$STAGE_DIR" && zip -q -r "$BUILD_DIR/beanstalk-app.zip" .)

echo "Built $BUILD_DIR/beanstalk-app.zip ($(du -h "$BUILD_DIR/beanstalk-app.zip" | cut -f1))"
