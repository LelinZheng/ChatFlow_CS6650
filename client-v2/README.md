# Client v2: Distributed Load Testing

WebSocket load test client for **CS6650 Assignment 2**, redesigned for the distributed server architecture.
All workers share a single message queue, connect to `/chat`, and encode room in the message body — enabling tunable concurrency across 64 / 128 / 256 / 512 threads without per-room connection management.

---

## 📋 Features

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

### Metrics
- Per-message latency (round-trip: send → Redis broadcast echo)
- Status codes: `OK`, `FAILED_AFTER_RETRIES`, `NO_CONNECTION`, `INVALID_MEMBERSHIP`
- Statistical analysis: mean, median, p95, p99, min, max latency
- Throughput per room and message type distribution
- Time-series throughput in 10-second buckets (queue profile graphs)
- Output files labeled per run: `summary_256w.txt`, `main_metrics_256w.csv`, `throughput_10s_256w.csv`

---

## 🏗️ Architecture

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
              ├── WebSocket send to /chat
              └── wait for echo (Redis broadcast)
                    └── MetricRecord → metricsQueue → CsvMetricsWriter
```

### Key Design: Why Wait for Echo?
Each message moves the worker's session to a new room on the server. The server only echoes the message back after it has been published to RabbitMQ, consumed, and re-broadcast via Redis. Sending the next message before receiving the echo would cause the session to switch rooms, orphaning the previous echo. The sequential send → wait → send protocol is required by this architecture.

---

## 🚀 Build and Run

### Prerequisites
- Java 21
- Maven 3.9+
- server-v2, consumer, RabbitMQ, and Redis running

### Build
```bash
mvn clean package
```

This creates:
- `target/chatflow-client-v2-1.0-SNAPSHOT-jar-with-dependencies.jar`

### Run
```bash
java -jar target/chatflow-client-v2-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## ⚙️ Configuration

Edit constants in `LoadTestRunner.java` before each run:

```java
private static final int TOTAL_WORKERS  = 256;   // tune: 64 / 128 / 256 / 512
private static final int TOTAL_MESSAGES = 500_000; // 1_000_000 for stress test
private static final int ROOMS          = 20;
private static final int QUEUE_CAPACITY = 10_000;
```

Edit the target URL in `LoadTestClient.java`:
```java
String httpBaseUrl = "http://localhost:8080";
URI wsBaseUri = URI.create("ws://localhost:8080/chat");

// For ALB:
// String httpBaseUrl = "http://chat-server-v2-ALB-191353243.us-west-2.elb.amazonaws.com";
// URI wsBaseUri = URI.create("ws://chat-server-v2-ALB-191353243.us-west-2.elb.amazonaws.com/chat");
```

---

## 📁 Output Files

After each run, files are saved to `../results/v2/` labeled with the worker count:

### `summary_256w.txt`
Human-readable results including load test stats and full latency analysis:
```
=== Load Test Results (256w) ===
workers=256 messages=500000 rooms=20
OK=499997 failed=3
timeSec=88.56
throughput msg/s=5646.04
connections=512
reconnections=286

=== Latency (OK only, ms) ===
mean=33.655
median=34.000
p95=41.000
p99=45.000
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

## 📊 Expected Output

```
Load Test Client Started
workers=256 messages=500000
Performing server health check...
✓ Server health check passed

=== Starting Load Test ===
workers=256 messages=500000 rooms=20

=== Load Test Results (256w) ===
OK=499997 failed=3
timeSec=88.56
throughput msg/s=5646.04
connections=512
reconnections=286
connectionFailures=0
deadWorkers=0

=== Metrics Summary ===
OK=499997 failed=3
durationSec=88.560

=== Latency (OK only, ms) ===
mean=33.655
median=34.000
p95=41.000
p99=45.000
min=1.000 max=200.000

=== Throughput per room (OK only, msg/s) ===
room 1: 280.10
...
room 20: 283.44

=== Message type distribution (all statuses) ===
TEXT: 440997
JOIN: 30001
LEAVE: 28999
```

---

## 🔍 Understanding the Metrics

### Status Codes
| Code | Meaning |
|---|---|
| `OK` | Message echoed back successfully |
| `FAILED_AFTER_RETRIES` | Failed after 5 retry attempts |
| `NO_CONNECTION` | Worker never connected |
| `INVALID_MEMBERSHIP` | TEXT or LEAVE sent when user not in room (dropped before send) |

### Throughput vs Assignment 1
Throughput (~5,000–7,000 msg/s with 256 workers) is lower than Assignment 1 (~13,000 msg/s) because the echo now travels through the full distributed pipeline: RabbitMQ → Consumer → Redis → Server → Client. Each message waits for this ~33ms round trip before the next is sent. Throughput scales linearly with worker count up to the pipeline saturation point.

---

## 🧪 Testing

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

## 🐛 Troubleshooting

### Server Not Reachable
```
✗ Server health check failed: Connection refused
```
Ensure server-v2 is running and the URL in `LoadTestClient.java` is correct.

### Workers Timeout (pool.awaitTermination)
Increase the timeout in `LoadTestRunner.java` for large message counts:
```java
if (!pool.awaitTermination(240, TimeUnit.SECONDS)) { ... }
```

### Out of Memory
```bash
java -Xmx4G -jar target/chatflow-client-v2-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 📁 Project Structure

```
client-v2/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/edu/northeastern/cs6650/client/
    │       ├── LoadTestClient.java              # Entry point, health check
    │       ├── generator/
    │       │   └── MessageGenerator.java        # Seed phase + main phase
    │       ├── loadtest/
    │       │   └── LoadTestRunner.java          # Orchestrator, labeled output
    │       ├── metrics/
    │       │   ├── CsvMetricsWriter.java
    │       │   ├── MetricRecord.java
    │       │   └── MetricsAnalyzer.java         # Stats + file save
    │       ├── model/
    │       │   ├── ChatMessage.java
    │       │   └── MessageType.java
    │       ├── util/
    │       │   ├── MessageFactory.java
    │       │   ├── RandomGenerator.java
    │       │   └── RoomMembershipTracker.java   # Thread-safe membership
    │       └── ws/
    │           └── ConnectionWorker.java        # WebSocket worker
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
../results/v2/
├── summary_64w.txt
├── summary_128w.txt
├── summary_256w.txt
├── summary_512w.txt
├── main_metrics_256w.csv
├── throughput_10s_256w.csv
└── graphs/                    ← generated by monitoring/plot_results.py
```

---

## 🔗 Related Documentation

- [Main Project README](../README.md)
- [Server v2](../server-v2/README.md)
- [Consumer](../consumer/README.md)
- [Monitoring & Graphs](../monitoring/)
- [Architecture Document](../doc/architecture.md)

---

## 📝 Notes

- Output files are labeled with the worker count so successive runs never overwrite each other
- The generator seed phase (1,000 JOINs) is included in the `TOTAL_MESSAGES` count
- `INVALID_MEMBERSHIP` drops happen at the worker level before any network call — they indicate a race between the generator and workers on the shared membership state, which is expected to be rare
