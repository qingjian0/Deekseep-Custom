#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

javac --release 8 -d "$TMP_DIR/classes" \
  "$MODULE_DIR/src/com/dsmod/probe/FeatureHookInstallRegistry.java" \
  "$MODULE_DIR/test/FeatureHookInstallRegistryRegression.java"
java -cp "$TMP_DIR/classes" com.dsmod.probe.FeatureHookInstallRegistryRegression
