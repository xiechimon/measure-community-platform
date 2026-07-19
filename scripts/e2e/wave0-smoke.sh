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

echo "7/10 admin(sensitive:view) 明文解码 idCard，且查询参数在日志中脱敏"
query_body="$TMP_DIR/query-population.json"
QUERY_PASSWORD="wave0-password-${RANDOM}-${RANDOM}"
request GET "$GATEWAY_URL/api/v1/population/persons?idCard=$ID_CARD&password=$QUERY_PASSWORD" "$query_body" -H "Authorization: Bearer $TOKEN"
assert_success "$query_body"
RETURNED_ID_CARD="$(jq -er --argjson id "$POPULATION_ID" '.data.records[] | select(.id == $id) | .idCard' "$query_body")"
# admin 持 population:sensitive:view → 响应里 idCard 明文解码；未授权角色的打码由 7c(gridA) 覆盖。
# 响应体不写日志，下面仍断言查询参数(idCard/password)在 community-info 日志中被 LogSanitizer 脱敏为 ***。
[[ "$RETURNED_ID_CARD" == "$ID_CARD" ]] || fail "admin with sensitive:view should see full idCard" "$query_body"
QUERY_TRACE_ID="$(awk 'tolower($1) == "traceid:" {gsub("\\r", "", $2); print $2; exit}' "$TMP_DIR/headers")"
[[ "$QUERY_TRACE_ID" =~ ^[[:xdigit:]]{32}$ ]] || fail "query response is missing a valid traceId" "$query_body"
INFO_LOGS="$(compose logs --no-color community-info)"
if grep -Fq -- "$ID_CARD" <<<"$INFO_LOGS" || grep -Fq -- "$QUERY_PASSWORD" <<<"$INFO_LOGS"; then
  fail "community-info logs leaked a sensitive query value"
fi
grep -Fq -- "$QUERY_TRACE_ID" <<<"$INFO_LOGS" || fail "community-info logs are missing the query traceId"
QUERY_LOG_LINE="$(grep -F -- "$QUERY_TRACE_ID" <<<"$INFO_LOGS" | grep -F -- "idCard=***&password=***" || true)"
[[ -n "$QUERY_LOG_LINE" ]] || fail "community-info logs are missing sanitized query details for the query traceId"

echo "7a/10 gridA login returns token (GRID data scope, no sensitive:view)"
grida_login_body="$TMP_DIR/login-gridA.json"
request POST "$GATEWAY_URL/api/v1/auth/login" "$grida_login_body" -H 'Content-Type: application/json' \
  --data '{"account":"gridA","password":"123456"}'
assert_success "$grida_login_body"
TOKEN_GRIDA="$(jq -er '.data.token | select(type == "string" and length > 20)' "$grida_login_body")"

ID_CARD_GRIDA="9$(date +%s)$(printf '%05d' "$RANDOM")"
echo "7b/10 gridA creates a population record (grid_id defaults from her own context)"
create_grida_body="$TMP_DIR/create-population-gridA.json"
create_grida_payload="$(jq -cn --arg idCard "$ID_CARD_GRIDA" '{type:"常住",name:"Wave0网格A",idCard:$idCard,gender:"女",phone:"13800138001"}')"
request POST "$GATEWAY_URL/api/v1/population/persons" "$create_grida_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_GRIDA" --data "$create_grida_payload"
assert_success "$create_grida_body"
POPULATION_ID_GRIDA="$(jq -er '.data | numbers' "$create_grida_body")"

echo "7c/10 gridA sees her own grid record, but idCard stays masked (no sensitive:view)"
query_grida_body="$TMP_DIR/query-population-gridA.json"
request GET "$GATEWAY_URL/api/v1/population/persons?idCard=$ID_CARD_GRIDA" "$query_grida_body" -H "Authorization: Bearer $TOKEN_GRIDA"
assert_success "$query_grida_body"
GRIDA_RETURNED_ID_CARD="$(jq -er --argjson id "$POPULATION_ID_GRIDA" '.data.records[] | select(.id == $id) | .idCard' "$query_grida_body")"
[[ "$GRIDA_RETURNED_ID_CARD" != "$ID_CARD_GRIDA" && "$GRIDA_RETURNED_ID_CARD" == *"*"* ]] || fail "gridA's own record idCard was not desensitized" "$query_grida_body"

echo "7d/10 admin sees gridA's record across grids, idCard fully decoded (sensitive:view)"
query_admin_grida_body="$TMP_DIR/query-population-admin-for-gridA.json"
request GET "$GATEWAY_URL/api/v1/population/persons?idCard=$ID_CARD_GRIDA" "$query_admin_grida_body" -H "Authorization: Bearer $TOKEN"
assert_success "$query_admin_grida_body"
ADMIN_VIEW_GRIDA_ID_CARD="$(jq -er --argjson id "$POPULATION_ID_GRIDA" '.data.records[] | select(.id == $id) | .idCard' "$query_admin_grida_body")"
[[ "$ADMIN_VIEW_GRIDA_ID_CARD" == "$ID_CARD_GRIDA" ]] || fail "admin did not see gridA's plaintext idCard" "$query_admin_grida_body"

echo "7e/10 gridA's GRID scope excludes admin's NULL-grid record (row isolation)"
query_grida_isolation_body="$TMP_DIR/query-population-gridA-isolation.json"
request GET "$GATEWAY_URL/api/v1/population/persons?idCard=$ID_CARD" "$query_grida_isolation_body" -H "Authorization: Bearer $TOKEN_GRIDA"
assert_success "$query_grida_isolation_body"
GRIDA_SEES_ADMIN_ROW_COUNT="$(jq -er --argjson id "$POPULATION_ID" '[.data.records[] | select(.id == $id)] | length' "$query_grida_isolation_body")"
[[ "$GRIDA_SEES_ADMIN_ROW_COUNT" == "0" ]] || fail "gridA's GRID scope leaked admin's NULL-grid record" "$query_grida_isolation_body"

echo "7f/10 gridC login returns token (GRID data scope, grid 1003)"
gridc_login_body="$TMP_DIR/login-gridC.json"
request POST "$GATEWAY_URL/api/v1/auth/login" "$gridc_login_body" -H 'Content-Type: application/json' \
  --data '{"account":"gridC","password":"123456"}'
assert_success "$gridc_login_body"
TOKEN_GRIDC="$(jq -er '.data.token | select(type == "string" and length > 20)' "$gridc_login_body")"

ID_CARD_GRIDC="9$(date +%s)$(printf '%05d' "$RANDOM")"
echo "7g/10 gridC creates a population record (grid_id defaults from her own context, grid 1003)"
create_gridc_body="$TMP_DIR/create-population-gridC.json"
create_gridc_payload="$(jq -cn --arg idCard "$ID_CARD_GRIDC" '{type:"常住",name:"Wave0网格C",idCard:$idCard,gender:"女",phone:"13800138002"}')"
request POST "$GATEWAY_URL/api/v1/population/persons" "$create_gridc_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_GRIDC" --data "$create_gridc_payload"
assert_success "$create_gridc_body"
POPULATION_ID_GRIDC="$(jq -er '.data | numbers' "$create_gridc_body")"

echo "7h/10 gridB login returns token (GRID data scope, grid 1002)"
gridb_login_body="$TMP_DIR/login-gridB.json"
request POST "$GATEWAY_URL/api/v1/auth/login" "$gridb_login_body" -H 'Content-Type: application/json' \
  --data '{"account":"gridB","password":"123456"}'
assert_success "$gridb_login_body"
TOKEN_GRIDB="$(jq -er '.data.token | select(type == "string" and length > 20)' "$gridb_login_body")"

ID_CARD_GRIDB="9$(date +%s)$(printf '%05d' "$RANDOM")"
echo "7i/10 gridB creates a population record (grid_id defaults from her own context, grid 1002)"
create_gridb_body="$TMP_DIR/create-population-gridB.json"
create_gridb_payload="$(jq -cn --arg idCard "$ID_CARD_GRIDB" '{type:"常住",name:"Wave0网格B",idCard:$idCard,gender:"男",phone:"13800138003"}')"
request POST "$GATEWAY_URL/api/v1/population/persons" "$create_gridb_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_GRIDB" --data "$create_gridb_payload"
assert_success "$create_gridb_body"
POPULATION_ID_GRIDB="$(jq -er '.data | numbers' "$create_gridb_body")"

echo "7j/10 commX login returns token (COMMUNITY data scope, community 10: grids 1001+1003)"
commx_login_body="$TMP_DIR/login-commX.json"
request POST "$GATEWAY_URL/api/v1/auth/login" "$commx_login_body" -H 'Content-Type: application/json' \
  --data '{"account":"commX","password":"123456"}'
assert_success "$commx_login_body"
TOKEN_COMMX="$(jq -er '.data.token | select(type == "string" and length > 20)' "$commx_login_body")"

echo "7k/10 commX's COMMUNITY scope spans both her grids (1001+1003) but excludes the sibling community's grid (1002)"
query_commx_body="$TMP_DIR/query-population-commX.json"
request GET "$GATEWAY_URL/api/v1/population/persons?page=1&size=50" "$query_commx_body" -H "Authorization: Bearer $TOKEN_COMMX"
assert_success "$query_commx_body"
COMMX_SEES_GRIDA_COUNT="$(jq -er --argjson id "$POPULATION_ID_GRIDA" '[.data.records[] | select(.id == $id)] | length' "$query_commx_body")"
[[ "$COMMX_SEES_GRIDA_COUNT" == "1" ]] || fail "commX's COMMUNITY scope should see gridA's record (grid 1001)" "$query_commx_body"
COMMX_SEES_GRIDC_COUNT="$(jq -er --argjson id "$POPULATION_ID_GRIDC" '[.data.records[] | select(.id == $id)] | length' "$query_commx_body")"
[[ "$COMMX_SEES_GRIDC_COUNT" == "1" ]] || fail "commX's COMMUNITY scope should see gridC's record (grid 1003, same community as grid 1001)" "$query_commx_body"
COMMX_SEES_GRIDB_COUNT="$(jq -er --argjson id "$POPULATION_ID_GRIDB" '[.data.records[] | select(.id == $id)] | length' "$query_commx_body")"
[[ "$COMMX_SEES_GRIDB_COUNT" == "0" ]] || fail "commX's COMMUNITY scope leaked gridB's record from the sibling community (grid 1002)" "$query_commx_body"
COMMX_RETURNED_GRIDA_ID_CARD="$(jq -er --argjson id "$POPULATION_ID_GRIDA" '.data.records[] | select(.id == $id) | .idCard' "$query_commx_body")"
[[ "$COMMX_RETURNED_GRIDA_ID_CARD" == *"*"* ]] || fail "commX lacks population:sensitive:view and should see a masked idCard" "$query_commx_body"

echo "7l/10 gridA's GRID scope is narrower than COMMUNITY: sees her own grid but not gridC's (same community, different grid)"
query_grida_community_body="$TMP_DIR/query-population-gridA-community.json"
request GET "$GATEWAY_URL/api/v1/population/persons?page=1&size=50" "$query_grida_community_body" -H "Authorization: Bearer $TOKEN_GRIDA"
assert_success "$query_grida_community_body"
GRIDA_SEES_OWN_COUNT="$(jq -er --argjson id "$POPULATION_ID_GRIDA" '[.data.records[] | select(.id == $id)] | length' "$query_grida_community_body")"
[[ "$GRIDA_SEES_OWN_COUNT" == "1" ]] || fail "gridA's GRID scope should still see her own record (grid 1001)" "$query_grida_community_body"
GRIDA_SEES_GRIDC_COUNT="$(jq -er --argjson id "$POPULATION_ID_GRIDC" '[.data.records[] | select(.id == $id)] | length' "$query_grida_community_body")"
[[ "$GRIDA_SEES_GRIDC_COUNT" == "0" ]] || fail "gridA's GRID scope leaked gridC's record from the same community but a different grid (1003)" "$query_grida_community_body"

echo "7m/10 admin creates a role (smokeRole, GRID data scope)"
create_role_body="$TMP_DIR/create-role.json"
create_role_payload='{"code":"smokeRole","name":"冒烟角色","dataScope":"GRID"}'
request POST "$GATEWAY_URL/api/v1/auth/roles" "$create_role_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" --data "$create_role_payload"
assert_success "$create_role_body"
ROLE_ID="$(jq -er '.data | numbers' "$create_role_body")"

echo "7n/10 duplicate role code is rejected"
dup_role_body="$TMP_DIR/create-role-dup.json"
request POST "$GATEWAY_URL/api/v1/auth/roles" "$dup_role_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" --data "$create_role_payload"
assert_status 409 "$dup_role_body"

echo "7o/10 invalid dataScope is rejected"
bad_scope_body="$TMP_DIR/create-role-badscope.json"
request POST "$GATEWAY_URL/api/v1/auth/roles" "$bad_scope_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" --data '{"code":"badScope","name":"x","dataScope":"CUSTOM"}'
assert_status 400 "$bad_scope_body"

echo "7p/10 admin lists permissions and finds population:query"
permissions_body="$TMP_DIR/permissions.json"
request GET "$GATEWAY_URL/api/v1/auth/permissions" "$permissions_body" -H "Authorization: Bearer $TOKEN"
assert_success "$permissions_body"
PERM_ID="$(jq -er '.data[] | select(.code == "population:query") | .id' "$permissions_body")"

echo "7q/10 admin assigns population:query permission to the new role"
assign_perm_body="$TMP_DIR/assign-role-permissions.json"
assign_perm_payload="$(jq -cn --argjson permId "$PERM_ID" '{permissionIds: [$permId]}')"
request PUT "$GATEWAY_URL/api/v1/auth/roles/$ROLE_ID/permissions" "$assign_perm_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" --data "$assign_perm_payload"
assert_success "$assign_perm_body"

echo "7r/10 gridA (lacks system:role:create) cannot create a role"
grida_role_body="$TMP_DIR/create-role-gridA.json"
request POST "$GATEWAY_URL/api/v1/auth/roles" "$grida_role_body" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN_GRIDA" --data '{"code":"x2","name":"x","dataScope":"GRID"}'
assert_status 403 "$grida_role_body"

echo "7s/10 admin deletes the smoke role (not bound to any user)"
delete_role_body="$TMP_DIR/delete-role.json"
request DELETE "$GATEWAY_URL/api/v1/auth/roles/$ROLE_ID" "$delete_role_body" -H "Authorization: Bearer $TOKEN"
assert_success "$delete_role_body"

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
