#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
grep -q 'startsWith("community-")' Jenkinsfile
! grep -q 'startsWith("cloud-")' Jenkinsfile
grep -q "defaultValue: 'main'" Jenkinsfile
grep -q 'scripts/ci/verify.sh' Jenkinsfile
! grep -q 'mvn install -DskipTests' Jenkinsfile
! grep -q 'DEPLOY_HOST = "服务器ip"' Jenkinsfile
