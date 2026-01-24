#!/bin/sh

# Ensure backups directory exists
mkdir -p /backups

# Create the backup script that will be run by cron
# We use export PGPASSWORD here to ensure pg_dump can authenticate
cat <<EOF > /usr/local/bin/run-backup.sh
#!/bin/sh
export PGPASSWORD="\$PGPASSWORD"
TIMESTAMP=\$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="/backups/backup_\$TIMESTAMP.sql.gz"

echo "Starting backup for \$DB_NAME at \$(date)..."
if pg_dump -h "\$DB_HOST" -U "\$DB_USER" -d "\$DB_NAME" | gzip > "\$BACKUP_FILE"; then
    echo "Backup successful: \$BACKUP_FILE"
    # Keep only the last 7 days of backups (optional, but good practice to avoid filling disk)
    find /backups -name "backup_*.sql.gz" -mtime +7 -delete
else
    echo "Backup failed!"
    rm -f "\$BACKUP_FILE"
fi
EOF

chmod +x /usr/local/bin/run-backup.sh

# Setup cron job to run at 4:00 AM daily
# Output logs to stdout/stderr of the container process (Docker logs)
echo "0 4 * * * /usr/local/bin/run-backup.sh > /proc/1/fd/1 2>&1" > /etc/crontabs/root

echo "Backup service started. Scheduled for 04:00 daily."

# Start crond in foreground
crond -f -d 8
