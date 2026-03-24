#!/bin/bash
# Polls RabbitMQ queue depths every 10s via the management HTTP API.
# Run on the RabbitMQ EC2 during a load test:
#   nohup bash monitor_queue.sh > queue_stats.log 2>&1 &
#
# Requires: curl, python3 (both available on Amazon Linux 2023 by default)

RABBITMQ_URL="http://localhost:15672"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASS="${RABBITMQ_PASS:-admin123}"

echo "timestamp,total_ready,total_unacked,room1,room2,room3,room4,room5,room6,room7,room8,room9,room10,room11,room12,room13,room14,room15,room16,room17,room18,room19,room20"

while true; do
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  resp=$(curl -s --max-time 5 -u "$RABBITMQ_USER:$RABBITMQ_PASS" \
    "$RABBITMQ_URL/api/queues/%2F")

  if [ $? -ne 0 ] || [ -z "$resp" ]; then
    echo "$ts,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR,ERROR"
    sleep 10
    continue
  fi

  # Parse totals and per-room depths
  result=$(echo "$resp" | python3 -c "
import sys, json
queues = json.load(sys.stdin)
q = {q['name']: q for q in queues}
total_ready   = sum(q.get('messages_ready', 0)     for q in queues)
total_unacked = sum(q.get('messages_unacknowledged', 0) for q in queues)
rooms = [str(q.get('room.' + str(i), {}).get('messages_ready', 0)) for i in range(1, 21)]
print(str(total_ready) + ',' + str(total_unacked) + ',' + ','.join(rooms))
" 2>/dev/null)

  echo "$ts,${result:-ERROR}"
  sleep 10
done
