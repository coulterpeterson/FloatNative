#!/bin/sh
# Dumps the Go service's Postgres data to JSON files for bin/import-postgres-export.php.
# Run on the server from packages/api-go (where docker-compose.yml lives):
#
#   sh ../api-php/scripts/export-postgres.sh ~/pg-export
#
# The files hold every API key, so they're readable only by the user running
# this. Delete them after importing (see README).
#
# qr_sessions is skipped: those rows expire after five minutes.
set -eu
umask 077

OUT="${1:-./pg-export}"
mkdir -p "$OUT"

for table in users playlists device_sessions fp_posts; do
    docker compose exec -T postgres psql -U postgres -d floatnative -qAt \
        -c "SET TIME ZONE 'UTC'" \
        -c "SELECT COALESCE(json_agg(t), '[]'::json) FROM $table t" \
        > "$OUT/$table.json"
    echo "Exported $table to $OUT/$table.json"
done
