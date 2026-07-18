#!/bin/bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd -P)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cp "$ROOT/.env.example" "$TMP_DIR/poison.env"
cat >>"$TMP_DIR/poison.env" <<'EOF'
COMPOSE_PROJECT_NAME=victim
NACOS_URL=http://outside.invalid:8848
GATEWAY_URL=http://outside.invalid:9090
AUTH_READY_URL=http://outside.invalid:9093/actuator/health/readiness
INFO_URL=http://outside.invalid:9094
INFO_READY_URL=http://outside.invalid:9094/actuator/health/readiness
GATEWAY_READY_URL=http://outside.invalid:9090/actuator/health/readiness
EOF

mkdir -p "$TMP_DIR/bin"
cat >"$TMP_DIR/bin/docker" <<'EOF'
#!/bin/bash
set -euo pipefail
printf '%s\n' "$*" >>"$TEST_DOCKER_LOG"
case " $* " in
  *" port mysql 3306 "*) echo '0.0.0.0:45001' ;;
  *" port redis 6379 "*) echo '0.0.0.0:45002' ;;
  *" port nacos 8848 "*) echo '0.0.0.0:45003' ;;
  *" port nacos 9848 "*) echo '0.0.0.0:45004' ;;
  *" port community-gateway 8080 "*) echo '0.0.0.0:45005' ;;
  *" port community-auth 8080 "*) echo '0.0.0.0:45006' ;;
  *" port community-info 8080 "*) echo '0.0.0.0:45007' ;;
esac
EOF
cat >"$TMP_DIR/bin/mvn" <<'EOF'
#!/bin/bash
exit 0
EOF
cat >"$TMP_DIR/bin/bash" <<'EOF'
#!/bin/bash
exec /bin/bash "$@"
EOF
cat >"$TMP_DIR/bin/curl" <<'EOF'
#!/bin/bash
set -euo pipefail
printf '%s\n' "$*" >>"$TEST_CURL_LOG"
url=""
output_file=""
has_get=0
for ((index = 1; index <= $#; index++)); do
  argument="${!index}"
  case "$argument" in
    http://*|https://*) url="$argument" ;;
    -o)
      next_index=$((index + 1))
      output_file="${!next_index}"
      ;;
    --get) has_get=1 ;;
  esac
done
printf 'URL %s\n' "$url" >>"$TEST_CURL_LOG"

# Respond to either host so the pre-fix run reaches the URL assertion instead
# of spending two minutes in bootstrap readiness retries.
if [[ "$url" == */nacos/* ]]; then
  case "$url" in
    */health/readiness)
      exit 0
      ;;
    */auth/login)
      if [[ -n "$output_file" ]]; then
        printf '%s' '{"accessToken":"fake-token"}' >"$output_file"
        printf '200'
      else
        printf '%s' '{"accessToken":"fake-token"}'
      fi
      exit 0
      ;;
    */console/namespaces)
      if [[ "$has_get" == 1 ]]; then
        printf '%s' '{"data":[]}'
      else
        printf '%s' 'true'
      fi
      exit 0
      ;;
    */cs/configs)
      if [[ "$has_get" == 1 ]]; then
        case " $* " in
          *"community-auth-dev.yml"*) printf '%s\n' 'jwt:'; printf '%s\n' 'username: ${DB_USERNAME}'; printf '%s\n' 'password: ${DB_PASSWORD}' ;;
          *"redis-common.yaml"*) printf '%s\n' 'password: ${REDIS_PASSWORD}' ;;
        esac
      else
        printf '%s' 'true'
      fi
      exit 0
      ;;
  esac
fi

# The smoke fixture deliberately fails on application readiness after the
# real bootstrap script has completed, so its cleanup path can be asserted.
exit 1
EOF
chmod +x "$TMP_DIR/bin"/*

assert_absent() {
  local needle="$1" file="$2" description="$3"
  if grep -Fq -- "$needle" "$file"; then
    echo "$description" >&2
    exit 1
  fi
}

assert_local_nacos_urls() {
  local file="$1"
  if grep '^URL ' "$file" | grep -F '/nacos/' | grep -Fv 'URL http://127.0.0.1:45003/nacos/' >/dev/null; then
    echo "bootstrap requested a non-local or non-dynamic Nacos URL" >&2
    exit 1
  fi
  grep -Fq 'URL http://127.0.0.1:45003/nacos/' "$file"
}

run_smoke_and_expect_first_request_failure() {
  local keep_stack="${1:-0}"
  : >"$TEST_DOCKER_LOG"
  : >"$TEST_CURL_LOG"
  set +e
  PATH="$TMP_DIR/bin:$PATH" TEST_ROOT="$ROOT" TEST_MODE=smoke \
    TEST_DOCKER_LOG="$TEST_DOCKER_LOG" TEST_CURL_LOG="$TEST_CURL_LOG" \
    KEEP_STACK="$keep_stack" ENV_FILE="$TMP_DIR/poison.env" \
    /bin/bash "$ROOT/scripts/e2e/wave0-smoke.sh" --setup >/dev/null 2>"$TMP_DIR/smoke.stderr"
  local status=$?
  set -e
  [[ "$status" -ne 0 ]] || { echo "smoke fixture must stop at fake curl" >&2; exit 1; }
}

export TEST_DOCKER_LOG="$TMP_DIR/docker.log"
export TEST_CURL_LOG="$TMP_DIR/curl.log"

run_smoke_and_expect_first_request_failure
assert_absent '-p victim' "$TEST_DOCKER_LOG" 'setup accepted COMPOSE_PROJECT_NAME from ENV_FILE'
grep -Fq 'http://127.0.0.1:45005/actuator/health/readiness' "$TEST_CURL_LOG"
assert_absent 'outside.invalid' "$TEST_CURL_LOG" 'bootstrap accepted poisoned external URL'
assert_local_nacos_urls "$TEST_CURL_LOG"
grep -Fq ' down -v' "$TEST_DOCKER_LOG"

run_smoke_and_expect_first_request_failure 1
assert_absent ' down -v' "$TEST_DOCKER_LOG" 'KEEP_STACK=1 must not remove the generated stack'
grep -Fq "Reuse: ENV_FILE=$TMP_DIR/poison.env" "$TMP_DIR/smoke.stderr"
grep -Eq 'COMPOSE_PROJECT_NAME=measure-community-wave0-e2e-[0-9]+-[0-9]+' "$TMP_DIR/smoke.stderr"
for binding in MYSQL_HOST_PORT=45001 REDIS_HOST_PORT=45002 NACOS_HOST_PORT=45003 NACOS_GRPC_HOST_PORT=45004 GATEWAY_HOST_PORT=45005 AUTH_HOST_PORT=45006 INFO_HOST_PORT=45007; do
  grep -Fq "$binding" "$TMP_DIR/smoke.stderr"
done

: >"$TEST_DOCKER_LOG"
: >"$TEST_CURL_LOG"
set +e
PATH="$TMP_DIR/bin:$PATH" TEST_ROOT="$ROOT" TEST_MODE=smoke \
  TEST_DOCKER_LOG="$TEST_DOCKER_LOG" TEST_CURL_LOG="$TEST_CURL_LOG" \
  COMPOSE_PROJECT_NAME=caller ENV_FILE="$TMP_DIR/poison.env" \
  /bin/bash "$ROOT/scripts/e2e/wave0-smoke.sh" --setup >/dev/null 2>&1
status=$?
set -e
[[ "$status" -ne 0 ]] || { echo "explicit-project smoke fixture must stop at fake curl" >&2; exit 1; }
grep -Fq -- '-p caller' "$TEST_DOCKER_LOG"
assert_absent '-p victim' "$TEST_DOCKER_LOG" 'explicit-project setup accepted env-file project'
assert_absent ' down -v' "$TEST_DOCKER_LOG" 'explicit shell project must not be auto-removed'
assert_absent 'outside.invalid' "$TEST_CURL_LOG" 'explicit-project bootstrap accepted poisoned external URL'
assert_local_nacos_urls "$TEST_CURL_LOG"

: >"$TEST_DOCKER_LOG"
: >"$TEST_CURL_LOG"
PATH="$TMP_DIR/bin:$PATH" TEST_ROOT="$ROOT" TEST_MODE=nacos \
  TEST_DOCKER_LOG="$TEST_DOCKER_LOG" TEST_CURL_LOG="$TEST_CURL_LOG" \
  COMPOSE_PROJECT_NAME=victim NACOS_URL=http://outside.invalid:8848 \
  /bin/bash "$ROOT/scripts/tests/nacos-bootstrap-it.sh" >/dev/null
assert_absent '-p victim' "$TEST_DOCKER_LOG" 'Nacos IT accepted a caller project'
assert_absent 'outside.invalid' "$TEST_CURL_LOG" 'Nacos IT bootstrap accepted an external URL'
assert_local_nacos_urls "$TEST_CURL_LOG"
grep -Fq ' down -v' "$TEST_DOCKER_LOG"

echo "E2E ownership behavior contract passed"
