#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "$0")/../.." && pwd -P)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/bin"
: >"$TMP_DIR/env"
cat >"$TMP_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

output_file=""
write_status=0
url=""
args=("$@")
for ((index = 0; index < ${#args[@]}; index++)); do
  case "${args[$index]}" in
    -o)
      output_file="${args[$((index + 1))]}"
      ;;
    -w)
      write_status=1
      ;;
  esac
  if [[ "${args[$index]}" == *"/nacos/"* ]]; then
    url="${args[$index]}"
  fi
done

if [[ "$url" == *"/auth/login" ]]; then
  body='{"message":"user name or password error"}'
  status=401
elif [[ "$url" == *"/auth/users/admin" ]]; then
  : >"${TEST_INIT_MARKER:?}"
  body='true'
  status=200
else
  body='{"status":"UP"}'
  status=200
fi

if [[ -n "$output_file" ]]; then
  printf '%s' "$body" >"$output_file"
else
  printf '%s' "$body"
fi
if [[ "$write_status" == 1 ]]; then
  printf '%s' "$status"
fi
EOF
chmod +x "$TMP_DIR/bin/curl"

if PATH="$TMP_DIR/bin:$PATH" \
  TEST_INIT_MARKER="$TMP_DIR/admin-init-called" \
  NACOS_ENV_FILE="$TMP_DIR/env" \
  NACOS_URL=http://nacos.test \
  NACOS_USERNAME=wrong-user \
  NACOS_PASSWORD=wrong-password \
  bash "$ROOT/scripts/nacos/bootstrap.sh" >/dev/null 2>"$TMP_DIR/stderr"; then
  echo "wrong Nacos credentials unexpectedly bootstrapped" >&2
  exit 1
fi

[[ ! -e "$TMP_DIR/admin-init-called" ]] || {
  echo "wrong credentials must not initialize the Nacos administrator" >&2
  exit 1
}
grep -Fq 'administrator initialization not attempted' "$TMP_DIR/stderr" || {
  cat "$TMP_DIR/stderr" >&2
  exit 1
}
echo "Nacos bootstrap login gate contract passed"
