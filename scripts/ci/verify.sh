#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
: "${SECURITY_INTERNAL_SECRET:?SECURITY_INTERNAL_SECRET is required}"
: "${JWT_SECRET:?JWT_SECRET is required}"
: "${SENSITIVE_AES_KEY:?SENSITIVE_AES_KEY is required}"
: "${SENSITIVE_HMAC_KEY:?SENSITIVE_HMAC_KEY is required}"
: "${NACOS_AUTH_TOKEN:?NACOS_AUTH_TOKEN is required}"
: "${NACOS_AUTH_IDENTITY_KEY:?NACOS_AUTH_IDENTITY_KEY is required}"
: "${NACOS_AUTH_IDENTITY_VALUE:?NACOS_AUTH_IDENTITY_VALUE is required}"
cleanup() { docker compose --profile app down -v || true; }
trap cleanup EXIT

echo "Unit gate"
mvn test
echo "Unit gate: PASS"
echo "Integration gate"
mvn -pl community-integration-tests -am -Pintegration verify
bash scripts/tests/compose-contract-test.sh
bash scripts/tests/nacos-bootstrap-it.sh
echo "Integration gate: PASS"
echo "System gate"
docker compose up -d --wait mysql redis nacos
mvn -N flyway:migrate \
  -Dflyway.url=jdbc:mysql://127.0.0.1:3306/measure_community \
  -Dflyway.user=root -Dflyway.password="$MYSQL_ROOT_PASSWORD"
bash scripts/nacos/bootstrap.sh
mvn package -DskipTests
docker compose --profile app up -d --build --wait
bash scripts/e2e/wave0-smoke.sh
echo "System gate: PASS"
echo "Capacity gate"
# Network name tracks COMPOSE_PROJECT_NAME (see scripts/e2e/wave0-smoke.sh):
# locally "measure-community-verify_default", in clean CI (unset) "measure-community_default".
docker run --rm --network "${COMPOSE_PROJECT_NAME:-measure-community}_default" \
  -e BASE_URL=http://community-gateway:8080 \
  -e ADMIN_ACCOUNT=admin -e ADMIN_PASSWORD=123456 \
  -i grafana/k6:0.52.0 run - < scripts/perf/wave0.js
echo "Capacity gate: PASS"
echo "Wave 0 verification: PASS"
