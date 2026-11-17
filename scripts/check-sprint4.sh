#!/bin/sh
# Run after docker compose up -d --build. Leaves one sample document in the archive.
set -eu
cd "$(dirname "$0")/.."
response=$(curl --fail --silent --show-error -F title=HelloWorld \
  -F 'file=@Test Artifacts/HelloWorld.pdf' http://localhost/api/documents)
id=$(printf '%s' "$response" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
test -n "$id"
attempt=0
while [ "$attempt" -lt 30 ]; do
  matches=$(curl --fail --silent --show-error 'http://localhost/api/documents/search?q=Hello')
  if printf '%s' "$matches" | grep -q "\"id\":$id[,}]"; then
    curl --fail --silent --show-error "http://localhost/api/documents/$id" | grep -q 'Hello'
    printf 'PASS: HelloWorld.pdf uploaded, extracted, indexed and found (id=%s).\n' "$id"
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 2
done
printf 'FAIL: document %s not found after 60 seconds.\n' "$id" >&2
exit 1
