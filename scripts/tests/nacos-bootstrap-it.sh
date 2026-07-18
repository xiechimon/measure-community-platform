#!/usr/bin/env bash
set -euo pipefail

for required in curl jq docker; do
  command -v "$required" >/dev/null || { echo "missing dependency: $required" >&2; exit 1; }
done

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd -P)"
ENV_FILE="$ROOT/.env.example"
TMP_DIR="$(mktemp -d)"
PROJECT=""
STACK_CREATED=0

cleanup() {
  local exit_status=$?
  trap - EXIT
  if [[ "$STACK_CREATED" == 1 ]]; then
    compose down -v || true
  fi
  rm -rf "$TMP_DIR"
  exit "$exit_status"
}
trap cleanup EXIT

[[ -f "$ENV_FILE" ]] || { echo "missing environment file: $ENV_FILE" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

readonly NACOS_NAMESPACE="74193cd9-fac4-4f2a-addc-47c60508b15c"
PROJECT="measure-community-nacos-bootstrap-it-${RANDOM}-$$"
NACOS_HOST_PORT=0
NACOS_GRPC_HOST_PORT=0
NACOS_URL=""

export COMPOSE_PROJECT_NAME="$PROJECT"
export NACOS_HOST_PORT
export NACOS_GRPC_HOST_PORT
export NACOS_RESTART_POLICY=no

compose() {
  docker compose --env-file "$ENV_FILE" -p "$PROJECT" -f "$ROOT/docker-compose.yml" "$@"
}

resolve_host_port() {
  local address
  address="$(compose port nacos "$1" | head -n 1)"
  [[ -n "$address" ]] || { echo "missing published Nacos port: $1" >&2; return 1; }
  printf '%s\n' "${address##*:}"
}

STACK_CREATED=1
compose up -d --wait nacos
NACOS_HOST_PORT="$(resolve_host_port 8848)"
NACOS_GRPC_HOST_PORT="$(resolve_host_port 9848)"
NACOS_URL="http://127.0.0.1:${NACOS_HOST_PORT}"
export NACOS_URL

NACOS_ENV_FILE="$ENV_FILE" bash "$ROOT/scripts/nacos/bootstrap.sh"
NACOS_ENV_FILE="$ENV_FILE" bash "$ROOT/scripts/nacos/bootstrap.sh"

login_response="$(printf '%s' "${NACOS_PASSWORD:?NACOS_PASSWORD is required}" | curl -fsS -X POST "$NACOS_URL/nacos/v1/auth/login" \
  --data-urlencode "username=${NACOS_USERNAME:?NACOS_USERNAME is required}" \
  --data-urlencode 'password@-')"
access_token="$(jq -er '.accessToken' <<<"$login_response")"

auth_config="$(printf '%s' "$access_token" | curl -fsS --get "$NACOS_URL/nacos/v1/cs/configs" \
  --data-urlencode "dataId=community-auth-dev.yml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "tenant=$NACOS_NAMESPACE" \
  --data-urlencode 'accessToken@-')"
grep -q '^jwt:' <<<"$auth_config"
grep -Fq '${DB_USERNAME}' <<<"$auth_config"
grep -Fq '${DB_PASSWORD}' <<<"$auth_config"

redis_config="$(printf '%s' "$access_token" | curl -fsS --get "$NACOS_URL/nacos/v1/cs/configs" \
  --data-urlencode "dataId=redis-common.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "tenant=$NACOS_NAMESPACE" \
  --data-urlencode 'accessToken@-')"
grep -Fq '${REDIS_PASSWORD}' <<<"$redis_config"
echo "Nacos bootstrap integration test passed"
