#!/usr/bin/env bash
# Backs up the database and the hidden tests to $WUOJ_DATA/backups (or $BACKUP_DIR), keeping 14 days.
# Run daily from cron (see README.md), and copy the backups off the server: they do not survive losing it.
set -euo pipefail
cd "$(dirname "$0")"
set -a
. ./.env
set +a

dest="${BACKUP_DIR:-$WUOJ_DATA/backups}"
stamp="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$dest"

docker compose exec -T postgres pg_dump --username=wuoj --dbname=wuoj --format=custom > "$dest/db-$stamp.dump"
tar --directory="$WUOJ_DATA" --exclude=testdata/.staging --create --gzip --file="$dest/testdata-$stamp.tar.gz" testdata

find "$dest" \( -name 'db-*.dump' -o -name 'testdata-*.tar.gz' \) -mtime +14 -delete
echo "Backed up to $dest: db-$stamp.dump, testdata-$stamp.tar.gz"
