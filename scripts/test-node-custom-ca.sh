#!/usr/bin/env bash
set -euo pipefail

# Usage: scripts/test-node-custom-ca.sh <locally-built-image>
# Requires Docker and OpenSSL. All certificates are generated for this test.
image="${1:?Specify the Docker image to test}"
task_test_dir="$(mktemp -d)"
trap 'rm -rf "$task_test_dir"' EXIT
mkdir "$task_test_dir/custom-ca"

openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
  -subj '/CN=Node custom CA test' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' \
  -keyout "$task_test_dir/ca.key" \
  -out "$task_test_dir/custom-ca/test-ca.crt" >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes \
  -subj '/CN=127.0.0.1' -keyout "$task_test_dir/server.key" \
  -out "$task_test_dir/server.csr" >/dev/null 2>&1
printf '%s\n' 'subjectAltName=IP:127.0.0.1' 'extendedKeyUsage=serverAuth' \
  > "$task_test_dir/server.ext"
openssl x509 -req -days 1 -in "$task_test_dir/server.csr" \
  -CA "$task_test_dir/custom-ca/test-ca.crt" -CAkey "$task_test_dir/ca.key" \
  -CAserial "$task_test_dir/ca.srl" -CAcreateserial -extfile "$task_test_dir/server.ext" \
  -out "$task_test_dir/server.crt" >/dev/null 2>&1

cat > "$task_test_dir/check.mjs" <<'JS'
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import https from 'node:https';

const server = https.createServer({
  key: readFileSync('/probe/server.key'),
  cert: readFileSync('/probe/server.crt'),
}, (_request, response) => response.end('ok'));
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
try {
  const result = await new Promise(resolve => {
    const request = https.get({
      hostname: '127.0.0.1', port: server.address().port, timeout: 5000,
    }, response => {
      response.resume();
      response.on('end', () => resolve({ status: response.statusCode }));
    });
    request.on('error', error => resolve({ error: error.code }));
    request.on('timeout', () => request.destroy(new Error('TLS request timed out')));
  });
  if (process.argv[2] === 'accept') {
    assert.equal(result.status, 200, JSON.stringify(result));
    console.log('PASS: Node trusts the CA installed through the certificate mount');
  } else {
    assert.match(result.error ?? '', /^(UNABLE_TO_VERIFY_LEAF_SIGNATURE|UNABLE_TO_GET_ISSUER_CERT_LOCALLY|SELF_SIGNED_CERT_IN_CHAIN)$/);
    console.log('PASS: Node rejects the server before its CA is installed');
  }
} finally {
  https.globalAgent.destroy();
  await new Promise(resolve => server.close(resolve));
}
JS

# The image runs as appuser, which must be able to read the generated fixtures.
chmod 755 "$task_test_dir" "$task_test_dir/custom-ca"
chmod 644 "$task_test_dir/server.key" "$task_test_dir/server.crt" \
  "$task_test_dir/check.mjs" "$task_test_dir/custom-ca/test-ca.crt"

run_case() {
  local expected="$1"
  shift
  docker run --rm --network none \
    --mount "type=bind,source=$task_test_dir,target=/probe,readonly" \
    "$@" --entrypoint sh "$image" \
    -c '/usr/local/bin/install-certificates && exec node /probe/check.mjs "$1"' \
    sh "$expected"
}

run_case reject
run_case accept --mount \
  "type=bind,source=$task_test_dir/custom-ca,target=/usr/local/share/ca-certificates,readonly"
