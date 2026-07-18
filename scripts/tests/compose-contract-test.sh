#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

services="$(docker compose --env-file .env.example config --services)"
for required in mysql redis nacos; do
  grep -qx "$required" <<<"$services" || { echo "missing service: $required"; exit 1; }
done

config="$(docker compose --env-file .env.example config --format json)"
jq -e '.services.mysql.ports[] | select(.host_ip == "127.0.0.1" and .target == 3306 and .published == "3306")' \
  <<<"$config" >/dev/null
jq -e '.services.redis.ports[] | select(.host_ip == "127.0.0.1" and .target == 6379 and .published == "6379")' \
  <<<"$config" >/dev/null
! grep -q 'expected-secret' docker-compose.yml
grep -Fq 'ENV_FILE="${NACOS_ENV_FILE:-$ROOT/.env}"' scripts/nacos/bootstrap.sh
grep -Fq 'missing Nacos environment file: $ENV_FILE' scripts/nacos/bootstrap.sh
grep -Fq 'password: ${MYSQL_ROOT_PASSWORD}' doc/community-auth-dev.yaml
grep -Fq 'password: ${MYSQL_ROOT_PASSWORD}' doc/community-info-dev.yaml
grep -Fq 'password: ${REDIS_PASSWORD}' doc/redis-common.yaml
