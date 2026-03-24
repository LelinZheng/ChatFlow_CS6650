#!/bin/bash
# Polls PostgreSQL stats every 10s and logs metrics to stdout (redirect to a file).
# Run on the DB EC2 during a load test:
#   nohup bash monitoring-v3/monitor_db.sh > db_stats.log 2>&1 &
#
# Requires PGPASSWORD to be set or .pgpass configured so psql doesn't prompt.
# Example: export PGPASSWORD=chatflow123

export PGPASSWORD=${PGPASSWORD:-chatflow123}
DB="psql -h localhost -U chatflow -d chatflow -t -A -c"

echo "timestamp,total_messages,active_connections,idle_connections"

while true; do
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  total=$($DB "SELECT COUNT(*) FROM messages;" 2>/dev/null | tr -d ' ')
  active=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE state='active';" 2>/dev/null | tr -d ' ')
  idle=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE state='idle';" 2>/dev/null | tr -d ' ')
  echo "$ts,${total:-ERROR},${active:-ERROR},${idle:-ERROR}"
  sleep 10
done
