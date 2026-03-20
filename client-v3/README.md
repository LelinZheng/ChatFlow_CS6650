# Client v3: Distributed Load Testing

WebSocket load test client for **CS6650 Assignment 3**, extended to collect end-to-end pipeline metrics including consumer DB write stats and server analytics.

---

## Features

### Load Testing
- Single shared `BlockingQueue` across all workers (no per-worker queues)
- Tunable total worker count: **64 / 128 / 256 / 512**
- All workers connect to `/chat` — room routing is in the message body
- No warmup phase; single main phase of 500K messages
- Configurable message count for stress tests (e.g. 1M)

### Message Generation
- **Seed phase**: 1,000 unique JOIN messages generated first, priming the membership map
- **Main phase**: 90% TEXT, 5% JOIN, 5% LEAVE — weighted distribution
- Generator-side membership map (single-threaded `HashMap`) ensures TEXT and LEAVE messages always reference a room the user has joined — no discard-and-retry loop
- Worker-side `RoomMembershipTracker` (`ConcurrentHashMap`) validates membership concurrently before each send

### Echo Matching
- Each worker sets an `expectedMessageId` before sending; only accepts an echo that contains its own ID
- On retry, a new UUID is generated so the server deduplicator does not block the retry

### Post-Test Collection
- Polls `GET /health/stats` on consumer-v3 until the RabbitMQ queue is drained and the batch writer buffer is empty, then fetches final DB write stats
- Calls `GET /api/metrics` on server-v3 to retrieve full analytics over all persisted messages
- All stats appended to the run's summary file

### Metrics
- Per-message latency (round-trip: send → Redis broadcast echo)
- Status codes: `OK`, `FAILED_AFTER_RETRIES`, `NO_CONNECTION`, `INVALID_MEMBERSHIP`
- Statistical analysis: mean, median, p95, p99, min, max latency
- Throughput per room and message type distribution
- Time-series throughput in 10-second buckets (queue profile graphs)
- Output files labeled per run: `summary_256w.txt`, `main_metrics_256w.csv`, `throughput_10s_256w.csv`

---

## Architecture

### Threading Model
```
Main Thread
  ├── MetricsWriter Thread     (writes CSV in real time)
  ├── Worker Pool (N threads)  (each holds one persistent WebSocket connection)
  │   └── all workers pull from one shared BlockingQueue
  └── Generator Thread
      ├── Seed phase: 1000 unique JOIN messages
      └── Main phase: 500K messages (90% TEXT / 5% JOIN / 5% LEAVE)
          └── N poison pills (one per worker) to signal shutdown
```

### Message Flow
```
MessageGenerator
  └── shared BlockingQueue (capacity 10 000)
        └── ConnectionWorker (×N)
              ├── RoomMembershipTracker.validate()
              ├── set expectedMessageId
              ├── WebSocket send to /chat
              └── wait for echo matching expectedMessageId (Redis broadcast)
                    └── MetricRecord → metricsQueue → CsvMetricsWriter
```

### Post-Test Flow
```
LoadTestClient (after runner.runLoadTest())
  ├── poll GET /health/stats  (every 5s, until drained or 10min timeout)
  ├── GET /health/stats       (final DB write stats → appended to summary)
  └── GET /api/metrics        (server analytics → logged to stdout)
```

### Key Design: Why Wait for Echo?
Each message moves the worker's session to a new room on the server. The server only echoes the message back after it has been published to RabbitMQ, consumed, and re-broadcast via Redis. Sending the next message before receiving the echo would cause the session to switch rooms, orphaning the previous echo. The sequential send → wait → send protocol is required by this architecture.

---

## Build and Run

### Prerequisites
- Java 21
- Maven 3.9+
- server-v3, consumer-v3, RabbitMQ, Redis, and PostgreSQL running

### Build
```bash
mvn clean package
```

This creates:
- `target/chatflow-client-v3-1.0-SNAPSHOT-jar-with-dependencies.jar`

### Run
```bash
java -jar target/chatflow-client-v3-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## Configuration

Edit constants in `LoadTestRunner.java` before each run:

```java
private static final int TOTAL_WORKERS  = 256;     // tune: 64 / 128 / 256 / 512
private static final int TOTAL_MESSAGES = 500_000; // 1_000_000 for stress test
private static final int ROOMS          = 20;
private static final int QUEUE_CAPACITY = 10_000;
```

Edit the target URLs in `LoadTestClient.java`:
```java
String serverUrl   = "http://localhost:8080";
String consumerUrl = "http://localhost:8081";
URI wsBaseUri = URI.create("ws://localhost:8080/chat");
```

---

## Output Files

After each run, files are saved to `../results/v3/` labeled with the worker count:

### `summary_256w.txt`
Human-readable results including load test stats, full latency analysis, and consumer DB stats:
```
=== Load Test Results (256w) ===
workers=256 messages=500000 rooms=20
OK=499998 failed=2
timeSec=88.56
throughput msg/s=5646.04
connections=512
reconnections=4

=== Latency (OK only, ms) ===
mean=33.655
median=34.000
p95=41.000
p99=45.000

=== Consumer DB Stats ===
consumer.received=500000
consumer.avgProcessingLatencyMs=3
db.written=500000
db.dropped=0
db.flushCount=2425
db.avgFlushLatencyMs=19
db.writtenPerSec=1278
```

### `main_metrics_256w.csv`
Per-message records:
```csv
timestamp,messageType,latencyMs,statusCode,roomId
1740000123456,TEXT,34,OK,5
1740000123490,JOIN,38,OK,12
```

### `throughput_10s_256w.csv`
10-second throughput buckets for queue profile graphs:
```csv
bucketStartMillis,count,throughputMsgPerSec
1740000120000,56460,5646.0
1740000130000,56440,5644.0
```

---

## Expected Output

```
Load Test Client Started
Performing server health check...
✓ Server health check passed: {"status":"UP",...}

Starting load test...

=== Load Test Results (256w) ===
...

Waiting for consumer to fully drain (RabbitMQ queue + DB buffer)...
  consumer.received=500000  db.bufferSize=1024  broadcastDlqSize=0
  consumer.received=500000  db.bufferSize=0     broadcastDlqSize=0
  consumer.received=500000  db.bufferSize=0     broadcastDlqSize=0
Consumer fully drained. Proceeding to metrics.

Fetching consumer DB stats...
=== Consumer DB Stats ===
consumer.received=500000
...

Fetching server metrics...
=== SERVER METRICS ===
{"totalMessages":500000,"coreQueryInputs":...}
======================
```

---

## Understanding the Metrics

### Status Codes
| Code | Meaning |
|---|---|
| `OK` | Message echoed back successfully |
| `FAILED_AFTER_RETRIES` | Failed after retry attempts |
| `NO_CONNECTION` | Worker never connected |
| `INVALID_MEMBERSHIP` | TEXT or LEAVE sent when user not in room (dropped before send) |

### Throughput vs Assignment 1
Throughput (~5,000–7,000 msg/s with 256 workers) is lower than Assignment 1 (~13,000 msg/s) because the echo now travels through the full distributed pipeline: RabbitMQ → Consumer → Redis → Server → Client. Each message waits for this ~33ms round trip before the next is sent. Throughput scales linearly with worker count up to the pipeline saturation point.

---

## Testing

### Run Unit Tests
```bash
mvn test
```

### Test Coverage
| Class | What is tested |
|---|---|
| `MessageGeneratorTest` | Seed phase, message counts, poison pills, membership invariants |
| `RoomMembershipTrackerTest` | join/leave/isMember, concurrent access |
| `ChatMessageTest` | Constructors, setters, poison pill detection |
| `MetricRecordTest` | Data structure validation |
| `MetricsAnalyzerTest` | Statistical calculations |
| `MessageFactoryTest` | Message generation ranges |
| `RandomGeneratorTest` | Boundary conditions |

---

## Troubleshooting

### Server Not Reachable
```
✗ Server health check failed: Connection refused
```
Ensure server-v3 is running and the URL in `LoadTestClient.java` is correct.

### Workers Timeout (pool.awaitTermination)
Increase the timeout in `LoadTestRunner.java` for large message counts:
```java
if (!pool.awaitTermination(240, TimeUnit.SECONDS)) { ... }
```

### Out of Memory
```bash
java -Xmx4G -jar target/chatflow-client-v3-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## Project Structure

```
client-v3/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/edu/northeastern/cs6650/client/
    │       ├── LoadTestClient.java              # Entry point, health check, post-test collection
    │       ├── generator/
    │       │   └── MessageGenerator.java        # Seed phase + main phase
    │       ├── loadtest/
    │       │   └── LoadTestRunner.java          # Orchestrator, labeled output
    │       ├── metrics/
    │       │   ├── CsvMetricsWriter.java        # Real-time CSV writer
    │       │   ├── MetricRecord.java            # Per-message record
    │       │   └── MetricsAnalyzer.java         # Stats + file save
    │       ├── model/
    │       │   ├── ChatMessage.java
    │       │   └── MessageType.java
    │       ├── util/
    │       │   ├── MessageFactory.java
    │       │   ├── RandomGenerator.java
    │       │   └── RoomMembershipTracker.java   # Thread-safe membership
    │       └── ws/
    │           └── ConnectionWorker.java        # WebSocket worker + echo matching
    └── test/
        └── java/edu/northeastern/cs6650/client/
            ├── generator/
            │   └── MessageGeneratorTest.java
            ├── metrics/
            │   ├── MetricRecordTest.java
            │   └── MetricsAnalyzerTest.java
            ├── model/
            │   └── ChatMessageTest.java
            └── util/
                ├── MessageFactoryTest.java
                ├── RandomGeneratorTest.java
                └── RoomMembershipTrackerTest.java
```

### Output Files (at repo root)
```
../results/v3/
├── summary_64w.txt
├── summary_128w.txt
├── summary_256w.txt
├── summary_512w.txt
├── main_metrics_256w.csv
├── throughput_10s_256w.csv
└── graphs/                    ← generated by monitoring/plot_results.py
```

---

## Related Documentation

- [Main Project README](../README.md)
- [Server v3](../server-v3/README.md)
- [Consumer v3](../consumer-v3/README.md)
- [Monitoring & Graphs](../monitoring/)
- [Architecture Document](../doc/architecture.md)

---

## Notes

- Output files are labeled with the worker count so successive runs never overwrite each other
- The generator seed phase (1,000 JOINs) is included in the `TOTAL_MESSAGES` count
- `INVALID_MEMBERSHIP` drops happen at the worker level before any network call — they indicate a race between the generator and workers on the shared membership state, which is expected to be rare
- The post-test drain poll prints 3 consecutive lines with stable `consumer.received` before declaring done
