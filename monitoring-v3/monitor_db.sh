#!/bin/bash
# Polls PostgreSQL stats + DB EC2 CPU/memory every 10s.
# Run on the DB EC2 during a load test:
#   nohup bash monitor_db.sh > db_stats.log 2>&1 &

export PGPASSWORD=${PGPASSWORD:-chatflow123}
DB="psql -h localhost -U chatflow -d chatflow -t -A -c"

echo "timestamp,total_messages,active_connections,idle_connections,cpu_pct,mem_used_mb,mem_free_mb"

while true; do
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # PostgreSQL stats
  total=$($DB "SELECT COUNT(*) FROM messages;" 2>/dev/null | tr -d ' ')
  active=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE state='active';" 2>/dev/null | tr -d ' ')
  idle=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE state='idle';" 2>/dev/null | tr -d ' ')

  # CPU usage (overall idle % subtracted from 100)
  cpu_idle=$(top -bn1 | grep "Cpu(s)" | awk '{print $8}' | tr -d '%')
  cpu_pct=$(echo "100 - ${cpu_idle:-0}" | bc 2>/dev/null)

  # Memory from /proc/meminfo (in MB)
  mem_total=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
  mem_free=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  mem_used=$((mem_total - mem_free))

  echo "$ts,${total:-ERROR},${active:-ERROR},${idle:-ERROR},${cpu_pct:-ERROR},${mem_used:-ERROR},${mem_free:-ERROR}"
  sleep 10
done
