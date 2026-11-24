#!/bin/sh
# Run after docker compose up -d --build. Leaves one sample document in the archive.
set -eu
cd "$(dirname "$0")/.."
base_url=${1:-http://localhost}

curl -fsS --retry 60 --retry-delay 2 --retry-max-time 120 --retry-connrefused "$base_url/api/health" >/dev/null
response=$(curl -fsS -F 'title=Sprint 5 check' \
  -F 'file=@Test Artifacts/pdf-test.pdf' "$base_url/api/documents")
id=$(printf '%s' "$response" | jq -er '.id')

attempt=0
while [ "$attempt" -lt 30 ]; do
  detail=$(curl -fsS "$base_url/api/documents/$id")
  matches=$(curl -fsS "$base_url/api/documents/search?q=Yukon")
  if printf '%s' "$detail" | jq -e '.status == "SUMMARY_GENERATED" and ((.summary // "") | length > 0)' >/dev/null &&
     printf '%s' "$matches" | jq -e --argjson id "$id" 'any(.[]; .id == $id)' >/dev/null; then
    printf 'PASS: document %s is searchable and has a saved summary:\n' "$id"
    printf '%s' "$detail" | jq -r '.summary'
    exit 0
  fi
  attempt=$((attempt + 1))
  sleep 2
done
printf 'FAIL: document %s is missing a summary or search result after 60 seconds.\n' "$id" >&2
exit 1
