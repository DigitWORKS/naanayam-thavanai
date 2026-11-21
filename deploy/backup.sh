#!/usr/bin/env bash
# Daily backup: MySQL dump + uploaded documents, kept 14 days (and copied to S3 if BACKUP_S3 is set).
# cron (as the deploy user):  30 1 * * *  /home/ubuntu/naanayam-thavanai/deploy/backup.sh >> /var/log/naanayam-backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"
set -a; [ -f .env ] && . ./.env; set +a

DIR=${BACKUP_DIR:-/var/backups/naanayam}
STAMP=$(date +%F-%H%M)
mkdir -p "$DIR"

docker compose exec -T db sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers naanayam' \
  | gzip > "$DIR/db-$STAMP.sql.gz"

docker run --rm -v naanayam_documents:/d:ro -v "$DIR":/b alpine \
  tar czf "/b/documents-$STAMP.tgz" -C /d .

find "$DIR" -name 'db-*.sql.gz' -mtime +14 -delete
find "$DIR" -name 'documents-*.tgz' -mtime +14 -delete

if [ -n "${BACKUP_S3:-}" ]; then
  aws s3 cp "$DIR/db-$STAMP.sql.gz" "$BACKUP_S3/"
  aws s3 cp "$DIR/documents-$STAMP.tgz" "$BACKUP_S3/"
fi
echo "$(date '+%F %T') backup ok: $STAMP"
