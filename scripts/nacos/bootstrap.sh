#!/usr/bin/env bash
set -euo pipefail

for required in curl jq; do
  command -v "$required" >/dev/null || { echo "missing dependency: $required" >&2; exit 1; }
done

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd -P)"
ENV_FILE="${NACOS_ENV_FILE:-$ROOT/.env}"
if [[ -n "${NACOS_ENV_FILE:-}" && ! -f "$ENV_FILE" ]]; then
  echo "missing Nacos environment file: $ENV_FILE" >&2
  exit 1
fi
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

readonly NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848}"
readonly NACOS_NAMESPACE="74193cd9-fac4-4f2a-addc-47c60508b15c"
readonly NACOS_NAMESPACE_NAME="measure-community"

wait_for_nacos() {
  local attempt
  for ((attempt = 1; attempt <= 60; attempt++)); do
    if curl -fsS "$NACOS_URL/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "Nacos readiness timed out: $NACOS_URL" >&2
  return 1
}

login() {
  local response
  response="$(curl -fsS -X POST "$NACOS_URL/nacos/v1/auth/login" \
    --data-urlencode "username=${NACOS_USERNAME:?NACOS_USERNAME is required}" \
    --data-urlencode "password=${NACOS_PASSWORD:?NACOS_PASSWORD is required}")"
  jq -er '.accessToken' <<<"$response"
}

ensure_namespace() {
  local access_token="$1"
  local namespaces create_result
  namespaces="$(curl -fsS --get "$NACOS_URL/nacos/v1/console/namespaces" \
    --data-urlencode "accessToken=$access_token")"
  if jq -e --arg namespace "$NACOS_NAMESPACE" \
    'any(.data[]?; .namespace == $namespace)' <<<"$namespaces" >/dev/null; then
    return 0
  fi

  create_result="$(curl -fsS -X POST "$NACOS_URL/nacos/v1/console/namespaces" \
    --data-urlencode "customNamespaceId=$NACOS_NAMESPACE" \
    --data-urlencode "namespaceName=$NACOS_NAMESPACE_NAME" \
    --data-urlencode "accessToken=$access_token")"
  [[ "$create_result" == "true" ]] || { echo "namespace creation failed" >&2; return 1; }
}

publish_config() {
  local access_token="$1"
  local source_file="$2"
  local data_id="$3"
  local result

  [[ "$source_file" == "$ROOT"/doc/* && -f "$source_file" ]] || {
    echo "invalid config source: $source_file" >&2
    return 1
  }

  result="$(curl -fsS -X POST "$NACOS_URL/nacos/v1/cs/configs" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "tenant=$NACOS_NAMESPACE" \
    --data-urlencode "content@$source_file" \
    --data-urlencode "accessToken=$access_token")"
  [[ "$result" == "true" ]] || { echo "publish failed: $data_id" >&2; return 1; }
}

wait_for_nacos
access_token="$(login)"
ensure_namespace "$access_token"

source_files=(
  "$ROOT/doc/common-config.yaml"
  "$ROOT/doc/redis-common.yaml"
  "$ROOT/doc/seata-common.yaml"
  "$ROOT/doc/rocketmq-common.yaml"
  "$ROOT/doc/community-gateway-dev.yaml"
  "$ROOT/doc/community-auth-dev.yaml"
  "$ROOT/doc/community-info-dev.yaml"
)
data_ids=(
  "common-config.yaml"
  "redis-common.yaml"
  "seata-common.yaml"
  "rocketmq-common.yaml"
  "community-gateway-dev.yaml"
  "community-auth-dev.yml"
  "community-info-dev.yaml"
)

for index in "${!source_files[@]}"; do
  publish_config "$access_token" "${source_files[$index]}" "${data_ids[$index]}"
done

echo "Nacos namespace and configuration bootstrap completed"
