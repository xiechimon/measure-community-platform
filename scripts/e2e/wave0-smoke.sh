#!/usr/bin/env bash
set -euo pipefail

# Run `bash scripts/e2e/wave0-smoke.sh --setup` for the deterministic local/CI
# setup plus all assertions.  Without --setup this script only verifies an
# already-running stack, which makes its initial failure a useful RED signal.

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd -P)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.example}"
SETUP_MODE="${1:-}"
usage() {
  echo "usage: $0 [--setup]" >&2
}

case "$SETUP_MODE" in
  ""|--setup) ;;
  *) usage; exit 2 ;;
esac

SHELL_COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
SHELL_MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-}"
SHELL_REDIS_HOST_PORT="${REDIS_HOST_PORT:-}"
SHELL_NACOS_HOST_PORT="${NACOS_HOST_PORT:-}"
SHELL_NACOS_GRPC_HOST_PORT="${NACOS_GRPC_HOST_PORT:-}"
SHELL_GATEWAY_HOST_PORT="${GATEWAY_HOST_PORT:-}"
SHELL_AUTH_HOST_PORT="${AUTH_HOST_PORT:-}"
SHELL_INFO_HOST_PORT="${INFO_HOST_PORT:-}"
GENERATED_PROJECT=0
STACK_CREATED=0
TMP_DIR="$(mktemp -d)"

cleanup() {
  local exit_status=$?
  trap - EXIT
  if [[ "$SETUP_MODE" == "--setup" && "$GENERATED_PROJECT" == 1 && "$STACK_CREATED" == 1 ]]; then
    if [[ "${KEEP_STACK:-0}" == 1 ]]; then
      echo "Wave 0 stack retained: $COMPOSE_PROJECT_NAME" >&2
      echo "Reuse: ENV_FILE=$ENV_FILE COMPOSE_PROJECT_NAME=$COMPOSE_PROJECT_NAME MYSQL_HOST_PORT=$MYSQL_HOST_PORT REDIS_HOST_PORT=$REDIS_HOST_PORT NACOS_HOST_PORT=$NACOS_HOST_PORT NACOS_GRPC_HOST_PORT=$NACOS_GRPC_HOST_PORT GATEWAY_HOST_PORT=$GATEWAY_HOST_PORT AUTH_HOST_PORT=$AUTH_HOST_PORT INFO_HOST_PORT=$INFO_HOST_PORT bash $0" >&2
    else
      compose down -v || true
    fi
  fi
  rm -rf "$TMP_DIR"
  exit "$exit_status"
}
trap cleanup EXIT

# The setup wrapper exports non-default host bindings so it never attaches to
# a developer's ordinary Compose dependencies.  Plain `docker compose` keeps
# its existing defaults through docker-compose.yml.

for required in curl jq docker mvn; do
  command -v "$required" >/dev/null || { echo "missing dependency: $required" >&2; exit 1; }
done
[[ -f "$ENV_FILE" ]] || { echo "missing ENV_FILE: $ENV_FILE" >&2; exit 1; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [[ "$SETUP_MODE" == "--setup" ]]; then
  if [[ -n "$SHELL_COMPOSE_PROJECT_NAME" ]]; then
    COMPOSE_PROJECT_NAME="$SHELL_COMPOSE_PROJECT_NAME"
  else
    COMPOSE_PROJECT_NAME="measure-community-wave0-e2e-${RANDOM}-$$"
    GENERATED_PROJECT=1
  fi
  MYSQL_HOST_PORT="${SHELL_MYSQL_HOST_PORT:-0}"
  REDIS_HOST_PORT="${SHELL_REDIS_HOST_PORT:-0}"
  NACOS_HOST_PORT="${SHELL_NACOS_HOST_PORT:-0}"
  NACOS_GRPC_HOST_PORT="${SHELL_NACOS_GRPC_HOST_PORT:-0}"
  GATEWAY_HOST_PORT="${SHELL_GATEWAY_HOST_PORT:-0}"
  AUTH_HOST_PORT="${SHELL_AUTH_HOST_PORT:-0}"
  INFO_HOST_PORT="${SHELL_INFO_HOST_PORT:-0}"
  GATEWAY_URL=""
  AUTH_READY_URL=""
  INFO_URL=""
  INFO_READY_URL=""
  GATEWAY_READY_URL=""
else
  [[ -n "$SHELL_COMPOSE_PROJECT_NAME" ]] || { echo "COMPOSE_PROJECT_NAME is required without --setup" >&2; exit 2; }
  COMPOSE_PROJECT_NAME="$SHELL_COMPOSE_PROJECT_NAME"
  MYSQL_HOST_PORT="${SHELL_MYSQL_HOST_PORT:-13306}"
  REDIS_HOST_PORT="${SHELL_REDIS_HOST_PORT:-16379}"
  NACOS_HOST_PORT="${SHELL_NACOS_HOST_PORT:-18848}"
  NACOS_GRPC_HOST_PORT="${SHELL_NACOS_GRPC_HOST_PORT:-19848}"
  GATEWAY_HOST_PORT="${SHELL_GATEWAY_HOST_PORT:-19090}"
  AUTH_HOST_PORT="${SHELL_AUTH_HOST_PORT:-19093}"
  INFO_HOST_PORT="${SHELL_INFO_HOST_PORT:-19094}"
  GATEWAY_URL="${GATEWAY_URL:-}"
  AUTH_READY_URL="${AUTH_READY_URL:-}"
  INFO_URL="${INFO_URL:-}"
  INFO_READY_URL="${INFO_READY_URL:-}"
  GATEWAY_READY_URL="${GATEWAY_READY_URL:-}"
fi
export COMPOSE_PROJECT_NAME MYSQL_HOST_PORT REDIS_HOST_PORT NACOS_HOST_PORT NACOS_GRPC_HOST_PORT
export GATEWAY_HOST_PORT AUTH_HOST_PORT INFO_HOST_PORT

compose() {
  docker compose --env-file "$ENV_FILE" -p "$COMPOSE_PROJECT_NAME" "$@"
}

resolve_host_port() {
  local service="$1" container_port="$2" address
  address="$(compose port "$service" "$container_port" | head -n 1)"
  [[ -n "$address" ]] || { echo "missing published port for $service:$container_port" >&2; return 1; }
  printf '%s\n' "${address##*:}"
}

resolve_dependency_ports() {
  MYSQL_HOST_PORT="$(resolve_host_port mysql 3306)"
  REDIS_HOST_PORT="$(resolve_host_port redis 6379)"
  NACOS_HOST_PORT="$(resolve_host_port nacos 8848)"
  NACOS_GRPC_HOST_PORT="$(resolve_host_port nacos 9848)"
}

resolve_application_ports() {
  GATEWAY_HOST_PORT="$(resolve_host_port community-gateway 8080)"
  AUTH_HOST_PORT="$(resolve_host_port community-auth 8080)"
  INFO_HOST_PORT="$(resolve_host_port community-info 8080)"
}

resolve_urls() {
  if [[ "$SETUP_MODE" == "--setup" ]]; then
    GATEWAY_URL="http://127.0.0.1:$GATEWAY_HOST_PORT"
    AUTH_READY_URL="http://127.0.0.1:$AUTH_HOST_PORT/actuator/health/readiness"
    INFO_URL="http://127.0.0.1:$INFO_HOST_PORT"
    INFO_READY_URL="$INFO_URL/actuator/health/readiness"
    GATEWAY_READY_URL="$GATEWAY_URL/actuator/health/readiness"
  else
    GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:$GATEWAY_HOST_PORT}"
    AUTH_READY_URL="${AUTH_READY_URL:-http://127.0.0.1:$AUTH_HOST_PORT/actuator/health/readiness}"
    INFO_URL="${INFO_URL:-http://127.0.0.1:$INFO_HOST_PORT}"
    INFO_READY_URL="${INFO_READY_URL:-$INFO_URL/actuator/health/readiness}"
    GATEWAY_READY_URL="${GATEWAY_READY_URL:-$GATEWAY_URL/actuator/health/readiness}"
  fi
}

request() {
  local method="$1" url="$2" output="$3"; shift 3
  HTTP_STATUS="$(curl -sS -D "$TMP_DIR/headers" -o "$output" -w '%{http_code}' -X "$method" "$url" "$@")"
}

fail() {
  local message="$1" body_file="${2:-}"
  echo "FAIL: $message (HTTP ${HTTP_STATUS:-n/a})" >&2
  [[ -n "$body_file" && -f "$body_file" ]] && { echo "body:" >&2; cat "$body_file" >&2; }
  exit 1
}

assert_status() {
  [[ "$HTTP_STATUS" == "$1" ]] || fail "expected HTTP $1, got $HTTP_STATUS" "$2"
}

assert_success() {
  assert_status 200 "$1"
  jq -e '.code == 200' "$1" >/dev/null || fail "expected successful response code" "$1"
}

wait_for_ready() {
  local name="$1" url="$2" body="$TMP_DIR/$1-ready.json"
  request GET "$url" "$body"
  assert_status 200 "$body"
  jq -e '.status == "UP"' "$body" >/dev/null || fail "$name readiness is not UP" "$body"
}

setup_stack() {
  export COMPOSE_PROJECT_NAME
  STACK_CREATED=1
  compose up -d --wait mysql redis nacos
  resolve_dependency_ports
  FLYWAY_PASSWORD="$MYSQL_ROOT_PASSWORD" mvn -N flyway:migrate \
    "-Dflyway.url=jdbc:mysql://127.0.0.1:${MYSQL_HOST_PORT}/measure_community" \
    "-Dflyway.user=root"
  NACOS_ENV_FILE="$ENV_FILE" NACOS_URL="http://127.0.0.1:$NACOS_HOST_PORT" \
    bash "$ROOT/scripts/nacos/bootstrap.sh"
  mvn package -DskipTests
  compose --profile app up -d --build --wait
  resolve_application_ports
  resolve_urls
  echo "Wave 0 setup project: $COMPOSE_PROJECT_NAME"
}

if [[ "$SETUP_MODE" == "--setup" ]]; then
  setup_stack
else
  resolve_urls
fi

echo "1/10 gateway/auth/info readiness"
wait_for_ready gateway "$GATEWAY_READY_URL"
wait_for_ready auth "$AUTH_READY_URL"
wait_for_ready info "$INFO_READY_URL"

echo "2/10 wrong password is rejected by gateway"
wrong_body="$TMP_DIR/wrong-password.json"
request POST "$GATEWAY_URL/api/v1/auth/login" "$wrong_body" -H 'Content-Type: application/json' \
  --data '{"account":"admin","password":"wrong-password"}'
assert_status 401 "$wrong_body"

echo "3/10 admin login returns token"
login_body="$TMP_DIR/login.json"
request POST "$GATEWAY_URL/api/v1/auth/login" "$login_body" -H 'Content-Type: application/json' \
  --data '{"account":"admin","password":"123456"}'
assert_success "$login_body"
TOKEN="$(jq -er '.data.token | select(type == "string" and length > 20)' "$login_body")"

echo "4/10 population request without token is rejected"
unauth_body="$TMP_DIR/unauth-population.json"
request GET "$GATEWAY_URL/api/v1/population/persons" "$unauth_body"
assert_status 401 "$unauth_body"

echo "5/10 token can read the current user through gateway"
user_body="$TMP_DIR/user.json"
request GET "$GATEWAY_URL/api/v1/auth/getUserName" "$user_body" -H "Authorization: Bearer $TOKEN"
assert_success "$user_body"
jq -er '.data == "超级管理员"' "$user_body" >/dev/null || fail "unexpected authenticated user" "$user_body"

ID_CARD="9$(date +%s)$(printf '%05d' "$RANDOM")"
echo "6/10 token creates a unique population record"
create_body="$TMP_DIR/create-population.json"
create_payload="$(jq -cn --arg idCard "$ID_CARD" '{type:"常住",name:"Wave0冒烟",idCard:$idCard,gender:"男",phone:"13800138000"}')"
request POST "$GATEWAY_URL/api/v1/population/persons" "$create_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" --data "$create_payload"
assert_success "$create_body"
POPULATION_ID="$(jq -er '.data | numbers' "$create_body")"

echo "7/10 query is desensitized and never returns the plaintext ID card"
query_body="$TMP_DIR/query-population.json"
QUERY_PASSWORD="wave0-password-${RANDOM}-${RANDOM}"
request GET "$GATEWAY_URL/api/v1/population/persons?idCard=$ID_CARD&password=$QUERY_PASSWORD" "$query_body" -H "Authorization: Bearer $TOKEN"
assert_success "$query_body"
RETURNED_ID_CARD="$(jq -er --argjson id "$POPULATION_ID" '.data.records[] | select(.id == $id) | .idCard' "$query_body")"
[[ "$RETURNED_ID_CARD" != "$ID_CARD" && "$RETURNED_ID_CARD" == *"*"* ]] || fail "ID card was not desensitized" "$query_body"
if grep -Fq -- "$ID_CARD" "$query_body"; then
  fail "query response leaked the plaintext ID card" "$query_body"
fi
QUERY_TRACE_ID="$(awk 'tolower($1) == "traceid:" {gsub("\\r", "", $2); print $2; exit}' "$TMP_DIR/headers")"
[[ "$QUERY_TRACE_ID" =~ ^[[:xdigit:]]{32}$ ]] || fail "query response is missing a valid traceId" "$query_body"
INFO_LOGS="$(compose logs --no-color community-info)"
if grep -Fq -- "$ID_CARD" <<<"$INFO_LOGS" || grep -Fq -- "$QUERY_PASSWORD" <<<"$INFO_LOGS"; then
  fail "community-info logs leaked a sensitive query value"
fi
grep -Fq -- "$QUERY_TRACE_ID" <<<"$INFO_LOGS" || fail "community-info logs are missing the query traceId"
QUERY_LOG_LINE="$(grep -F -- "$QUERY_TRACE_ID" <<<"$INFO_LOGS" | grep -F -- "idCard=***&password=***" || true)"
[[ -n "$QUERY_LOG_LINE" ]] || fail "community-info logs are missing sanitized query details for the query traceId"

echo "8/10 direct info access without internal header is forbidden"
direct_body="$TMP_DIR/direct-info.json"
request GET "$INFO_URL/api/v1/population/persons" "$direct_body"
assert_status 403 "$direct_body"

echo "9/10 database stores ciphertext and a 64-character HMAC"
db_row="$(printf 'SELECT id_card, id_card_hmac FROM t_population WHERE id = %s\n' "$POPULATION_ID" | \
  compose exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot measure_community')"
DB_ID_CARD="${db_row%%$'\t'*}"
DB_HMAC="${db_row#*$'\t'}"
[[ -n "$DB_ID_CARD" && "$DB_ID_CARD" != "$ID_CARD" ]] || fail "database retained plaintext ID card"
[[ "$DB_HMAC" =~ ^[[:xdigit:]]{64}$ ]] || fail "database HMAC must be exactly 64 hexadecimal characters"

echo "10/10 gateway response has a 32-hex traceId header"
trace_body="$TMP_DIR/trace.json"
request GET "$GATEWAY_URL/api/v1/auth/getUserName" "$trace_body" -H "Authorization: Bearer $TOKEN"
assert_success "$trace_body"
TRACE_ID="$(awk 'tolower($1) == "traceid:" {gsub("\\r", "", $2); print $2; exit}' "$TMP_DIR/headers")"
[[ "$TRACE_ID" =~ ^[[:xdigit:]]{32}$ ]] || fail "missing or invalid traceId header" "$trace_body"

echo "Wave 0 smoke: PASS"
