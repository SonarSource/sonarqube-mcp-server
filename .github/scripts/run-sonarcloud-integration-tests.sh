#!/usr/bin/env bash
set -euo pipefail

./gradlew :its:sonarCloudIntegrationTest --info
