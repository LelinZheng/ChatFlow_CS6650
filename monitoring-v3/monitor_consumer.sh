#!/bin/bash
# Polls consumer-v3 /health/stats every 10s and logs metrics to stdout (redirect to a file).
# Run on the consumer EC2 during a load test:
#   nohup bash monitoring-v3/monitor_consumer.sh > consumer_stats.log 2>&1 &

CONSUMER_URL="http://localhost:8081"

echo "timestamp,received,bufferSize,written,dropped,flushCount,avgFlushLatencyMs,writtenPerSec,dlqSize"

while true; do
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  resp=$(curl -s --max-time 5 "$CONSUMER_URL/health/stats")
  if [ $? -eq 0 ]; then
    received=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('consumer.received',0))")
    bufferSize=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('db.bufferSize',0))")
    written=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('db.written',0))")
    dropped=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('db.dropped',0))")
    flushCount=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('db.flushCount',0))")
    latency=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('db.avgFlushLatencyMs',0))")
    wps=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('db.writtenPerSec',0))")
    dlq=$(echo $resp | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('consumer.broadcastDlqSize',0))")
    echo "$ts,$received,$bufferSize,$written,$dropped,$flushCount,$latency,$wps,$dlq"
  else
    echo "$ts,ERROR: could not reach consumer"
  fi
  sleep 10
done
