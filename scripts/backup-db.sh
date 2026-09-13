#!/usr/bin/env bash
# Backup the Postgres database from the docker compose `db` service.
# Usage: ./scripts/backup-db.sh [output-file]
#
# The output contains personal data — keep it encrypted and outside the repo.
# Automate with cron, e.g.:
#   0 2 * * * cd /path/to/mediara-ai && ./scripts/backup-db.sh

set -euo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${1:-backups/mediara-$STAMP.sql.gz}"
mkdir -p "$(dirname "$OUT")"

echo "Dumping Postgres database -> $OUT"
docker compose exec -T db pg_dump -U "${POSTGRES_USER:-mediara}" -d "${POSTGRES_DB:-mediara}" | gzip > "$OUT"
echo "Backup complete: $OUT"