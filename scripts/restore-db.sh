#!/usr/bin/env bash
# Restore a gzipped pg_dump created by scripts/backup-db.sh into the
# docker compose `db` service.
# Usage: ./scripts/restore-db.sh backups/mediara-YYYYMMDD-HHMMSS.sql.gz

set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <backup.sql.gz>" >&2
  exit 1
fi

gunzip -c "$1" | docker compose exec -T db psql -U "${POSTGRES_USER:-mediara}" -d "${POSTGRES_DB:-mediara}"
echo "Restore complete."