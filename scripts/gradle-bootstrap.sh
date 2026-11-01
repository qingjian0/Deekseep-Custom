#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="9.8.2"
GRADLE_SHA256="21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"
GRADLE_BIN=""

if command -v gradle >/dev/null 2>&1; then
    INSTALLED_VERSION="$(gradle --version 2>/dev/null | awk '/^Gradle / { print $2; exit }' || true)"
    if [[ "$INSTALLED_VERSION" == "$GRADLE_VERSION" ]]; then
        GRADLE_BIN="$(command -v gradle)"
    fi
fi

if [[ -z "$GRADLE_BIN" ]]; then
    CACHE_HOME="${XDG_CACHE_HOME:-$HOME/.cache}"
    GRADLE_HOME="$CACHE_HOME/yunseek/gradle-$GRADLE_VERSION"
    GRADLE_BIN="$GRADLE_HOME/bin/gradle"

    if [[ ! -x "$GRADLE_BIN" ]]; then
        command -v curl >/dev/null 2>&1 || {
            echo "curl is required to bootstrap Gradle." >&2
            exit 1
        }
        command -v unzip >/dev/null 2>&1 || {
            echo "unzip is required to bootstrap Gradle." >&2
            exit 1
        }
        command -v sha256sum >/dev/null 2>&1 || {
            echo "sha256sum is required to verify Gradle." >&2
            exit 1
        }

        mkdir -p "$CACHE_HOME/yunseek"
        TEMP_HOME="$(mktemp -d "$CACHE_HOME/yunseek/gradle-bootstrap.XXXXXX")"
        trap 'rm -rf "$TEMP_HOME"' EXIT

        curl --fail --location --retry 3 \
            --output "$TEMP_HOME/gradle.zip" \
            "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
        printf '%s  %s\n' "$GRADLE_SHA256" "$TEMP_HOME/gradle.zip" \
            | sha256sum --check -
        unzip -q "$TEMP_HOME/gradle.zip" -d "$TEMP_HOME"

        rm -rf "$GRADLE_HOME"
        mv "$TEMP_HOME/gradle-$GRADLE_VERSION" "$GRADLE_HOME"
        trap - EXIT
        rm -rf "$TEMP_HOME"
    fi
fi

test -x "$GRADLE_BIN" || {
    echo "Pinned Gradle executable is unavailable: $GRADLE_BIN" >&2
    exit 1
}
printf '%s\n' "$GRADLE_BIN"
