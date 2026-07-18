#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

grep -Fq 'SHELL_COMPOSE_PROJECT_NAME=' scripts/e2e/wave0-smoke.sh
grep -Fq ':-0}' scripts/e2e/wave0-smoke.sh
grep -Fq 'docker compose --env-file' scripts/e2e/wave0-smoke.sh
grep -Fq ' port ' scripts/e2e/wave0-smoke.sh
grep -Fq 'PROJECT="measure-community-nacos-bootstrap-it-' scripts/tests/nacos-bootstrap-it.sh
grep -Fq 'NACOS_HOST_PORT=0' scripts/tests/nacos-bootstrap-it.sh
grep -Fq 'docker compose --env-file' scripts/tests/nacos-bootstrap-it.sh
grep -Fq ' port ' scripts/tests/nacos-bootstrap-it.sh

grep -Fq 'KEEP_STACK' scripts/e2e/wave0-smoke.sh
grep -Fq 'compose down -v' scripts/e2e/wave0-smoke.sh
grep -Fq 'trap cleanup EXIT' scripts/e2e/wave0-smoke.sh

if rg -n 'measure-community-(wave0-e2e|nacos-bootstrap-it)"$|readonly NACOS_HOST_PORT="38848"|readonly MYSQL_HOST_PORT="13306"' \
  scripts/e2e/wave0-smoke.sh scripts/tests/nacos-bootstrap-it.sh; then
  echo "isolated setup must not use a shared project name or fixed host port" >&2
  exit 1
fi

if rg -n 'PROJECT="\$\{COMPOSE_PROJECT_NAME|NACOS_URL="\$\{NACOS_URL' scripts/tests/nacos-bootstrap-it.sh; then
  echo "Nacos IT must own its generated project and local URL" >&2
  exit 1
fi

echo "E2E isolation contract passed"
