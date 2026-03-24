#!/bin/bash
# Polls PostgreSQL stats + DB EC2 CPU/memory every 10s.
# Run on the DB EC2 during a load test:
#   nohup bash monitor_db.sh > db_stats.log 2>&1 &

export PGPASSWORD=${PGPASSWORD:-chatflow123}
DB="psql -h localhost -U chatflow -d chatflow -t -A -c"

echo "timestamp,total_messages,active_connections,idle_connections,cpu_pct,mem_used_mb,mem_free_mb,blks_hit,blks_read,cache_hit_ratio_pct,locks_waiting,blks_written,buffers_checkpoint"

while true; do
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  # Row count and connections
  total=$($DB "SELECT COUNT(*) FROM messages;" 2>/dev/null | tr -d ' ')
  active=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE state='active';" 2>/dev/null | tr -d ' ')
  idle=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE state='idle';" 2>/dev/null | tr -d ' ')

  # Lock wait time (number of queries currently waiting on a lock)
  locks_waiting=$($DB "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type='Lock';" 2>/dev/null | tr -d ' ')

  # Buffer pool hit ratio (blks_hit / (blks_hit + blks_read)) from pg_stat_bgwriter
  stats=$($DB "SELECT blks_hit, blks_read, buffers_checkpoint, buffers_written FROM pg_stat_bgwriter;" 2>/dev/null)
  blks_hit=$(echo "$stats" | awk -F'|' '{print $1}' | tr -d ' ')
  blks_read=$(echo "$stats" | awk -F'|' '{print $2}' | tr -d ' ')
  buffers_checkpoint=$(echo "$stats" | awk -F'|' '{print $3}' | tr -d ' ')
  blks_written=$(echo "$stats" | awk -F'|' '{print $4}' | tr -d ' ')

  # Cache hit ratio %
  if [ -n "$blks_hit" ] && [ -n "$blks_read" ] && [ "$((blks_hit + blks_read))" -gt 0 ]; then
    cache_hit_ratio=$(echo "scale=2; $blks_hit * 100 / ($blks_hit + $blks_read)" | bc 2>/dev/null)
  else
    cache_hit_ratio="N/A"
  fi

  # CPU usage
  cpu_idle=$(top -bn1 | grep "Cpu(s)" | awk '{print $8}' | tr -d '%')
  cpu_pct=$(echo "100 - ${cpu_idle:-0}" | bc 2>/dev/null)

  # Memory
  mem_total=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
  mem_free=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  mem_used=$((mem_total - mem_free))

  echo "$ts,${total:-ERROR},${active:-ERROR},${idle:-ERROR},${cpu_pct:-ERROR},${mem_used:-ERROR},${mem_free:-ERROR},${blks_hit:-ERROR},${blks_read:-ERROR},${cache_hit_ratio:-ERROR},${locks_waiting:-ERROR},${blks_written:-ERROR},${buffers_checkpoint:-ERROR}"

  sleep 10
done
