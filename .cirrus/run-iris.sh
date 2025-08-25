#!/bin/bash
set -euo pipefail

: "${ARTIFACTORY_USERNAME?}" "${ARTIFACTORY_ACCESS_TOKEN?}" "${ARTIFACTORY_URL?}"
: "${SONAR_SQC_EU_URL?}" "${SONAR_IRIS_SQC_EU_TOKEN?}"
: "${SONAR_SQC_US_URL?}" "${SONAR_IRIS_SQC_US_TOKEN?}"
: "${SONAR_NEXT_URL?}" "${SONAR_IRIS_NEXT_TOKEN?}"

# Run IRIS from SQC EU to SQS
function run_iris_next () {
  java \
    -Diris.source.projectKey="SonarSource_sonarqube-mcp-server" \
    -Diris.source.organization="SonarSource" \
    -Diris.source.url="$SONAR_SQC_EU_URL" \
    -Diris.source.token="$SONAR_IRIS_SQC_EU_TOKEN" \
    -Diris.destination.projectKey="SonarSource_sonarqube-mcp-server" \
    -Diris.destination.url="$SONAR_NEXT_URL" \
    -Diris.destination.token="$SONAR_IRIS_NEXT_TOKEN" \
    -Diris.dryrun=$1 \
    -jar iris-\[RELEASE\]-jar-with-dependencies.jar
}

# Run IRIS from SQC EU to SQC US
function run_iris_sqc_us () {
  java \
    -Diris.source.projectKey="SonarSource_sonarqube-mcp-server" \
    -Diris.source.organization="SonarSource" \
    -Diris.source.url="$SONAR_SQC_EU_URL" \
    -Diris.source.token="$SONAR_IRIS_SQC_EU_TOKEN" \
    -Diris.destination.projectKey="SonarSource_sonarqube-mcp-server" \
    -Diris.destination.organization="SonarSource" \
    -Diris.destination.url="$SONAR_SQC_US_URL" \
    -Diris.destination.token="$SONAR_IRIS_SQC_US_TOKEN" \
    -Diris.dryrun=$1 \
    -jar iris-\[RELEASE\]-jar-with-dependencies.jar
}

VERSION="\[RELEASE\]"
HTTP_CODE=$(\
  curl \
    --write-out '%{http_code}' \
    --location \
    --remote-name \
    --user "$ARTIFACTORY_USERNAME:$ARTIFACTORY_ACCESS_TOKEN" \
    "$ARTIFACTORY_URL/sonarsource-private-releases/com/sonarsource/iris/iris/$VERSION/iris-$VERSION-jar-with-dependencies.jar"\
)

if [ "$HTTP_CODE" != "200" ]; then
  echo "Download $VERSION failed -> $HTTP_CODE"
  exit 1
else
  echo "Downloaded $VERSION"
fi

echo "===== Execute IRIS Next as dry-run"
run_iris_next "true"
STATUS=$?
if [ $STATUS -ne 0 ]; then
  echo "===== Failed to run IRIS dry-run"
  exit 1
else
  echo "===== Successful IRIS Next dry-run - executing IRIS for real."
  run_iris_next "false"
fi

echo "===== Execute IRIS SQC US as dry-run"
run_iris_sqc_us "true"
STATUS=$?
if [ $STATUS -ne 0 ]; then
  echo "===== Failed to run IRIS dry-run"
  exit 1
else
  echo "===== Successful IRIS SQC US dry-run - executing IRIS for real."
  run_iris_sqc_us "false"
fi
