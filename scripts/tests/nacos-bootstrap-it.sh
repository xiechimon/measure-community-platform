#!/usr/bin/env bash
set -euo pipefail

for required in curl jq docker; do
  command -v "$required" >/dev/null || { echo "missing dependency: $required" >&2; exit 1; }
done

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd -P)"
ENV_FILE="$ROOT/.env.example"
[[ -f "$ENV_FILE" ]] || { echo "missing environment file: $ENV_FILE" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

readonly NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848}"
readonly NACOS_NAMESPACE="74193cd9-fac4-4f2a-addc-47c60508b15c"

cleanup() {
  docker compose --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml" stop nacos || true
}
trap cleanup EXIT

docker compose --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml" up -d nacos

NACOS_ENV_FILE="$ENV_FILE" bash "$ROOT/scripts/nacos/bootstrap.sh"
NACOS_ENV_FILE="$ENV_FILE" bash "$ROOT/scripts/nacos/bootstrap.sh"

login_response="$(curl -fsS -X POST "$NACOS_URL/nacos/v1/auth/login" \
  --data-urlencode "username=${NACOS_USERNAME:?NACOS_USERNAME is required}" \
  --data-urlencode "password=${NACOS_PASSWORD:?NACOS_PASSWORD is required}")"
access_token="$(jq -er '.accessToken' <<<"$login_response")"

auth_config="$(curl -fsS --get "$NACOS_URL/nacos/v1/cs/configs" \
  --data-urlencode "dataId=community-auth-dev.yml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "tenant=$NACOS_NAMESPACE" \
  --data-urlencode "accessToken=$access_token")"
grep -q '^jwt:' <<<"$auth_config"
grep -Fq '${MYSQL_ROOT_PASSWORD}' <<<"$auth_config"

redis_config="$(curl -fsS --get "$NACOS_URL/nacos/v1/cs/configs" \
  --data-urlencode "dataId=redis-common.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "tenant=$NACOS_NAMESPACE" \
  --data-urlencode "accessToken=$access_token")"
grep -Fq '${REDIS_PASSWORD}' <<<"$redis_config"
echo "Nacos bootstrap integration test passed"
