# Client Part 1: Basic Load Testing

Multithreaded WebSocket client that performs high-volume load testing with basic metrics reporting.

---

## 📋 Features

### Warmup Phase
- 32 concurrent connections
- 1,000 messages per thread (32,000 total)
- Messages distributed across rooms
- Room 1-12 has 2 connections, Room 13-20 has 1 connection
- Connections kept open for main phase


### Main Phase
- 560 concurrent connections (28 per room)
- 500,000 total messages
- Random distribution across 20 rooms
- Persistent WebSocket connections

### Basic Metrics
- Total successful messages
- Total failed messages
- Runtime (wall time)
- Overall throughput (msg/s)
- Connection statistics

---

## 🏗️ Architecture

### Threading Model
```
Main Thread
  ├── Warmup: 32 Worker Threads (kept alive)
  │   └── 1000 messages each → Various rooms
  ├── Main Phase: Reuse 32 + Create 528 new (560 total)
  ├── Generator Thread (produces 500K messages)
  └── Metrics aggregation
```

### Message Flow
```
Generator → [Queue 1] → Worker 1 → WebSocket → Server
         → [Queue 2] → Worker 2 → WebSocket → Server
         → ...
         → [Queue 560] → Worker 560 → WebSocket → Server
```

---

## 🚀 Build and Run

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- Server running on target URL

### Build
```bash
mvn clean package
```

This creates:
- `target/chatflow-client-part1-1.0-SNAPSHOT.jar`
- `target/chatflow-client-part1-1.0-SNAPSHOT-jar-with-dependencies.jar` (fat JAR)

### Run
```bash
# Using fat JAR (recommended)
java -jar target/chatflow-client-part1-1.0-SNAPSHOT-jar-with-dependencies.jar

# With custom JVM options
java -Xmx2G -jar target/chatflow-client-part1-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## ⚙️ Configuration

Edit `LoadTestClient.java` to change settings:
```java
// Server configuration
String httpBaseUrl = "http://your-server:8080";
URI wsBaseUri = URI.create("ws://your-server:8080/chat/");

// In LoadTestRunner.java, adjust thread counts
int warmupThread = 32;              // Warmup threads
int connPerRoom = 28;               // Connections per room
int rooms = 20;                     // Number of rooms
int totalMsg = 500_000;             // Total messages
```

---

## 📊 Expected Output

### Warmup Phase
```
Load Test Client Started
Performing server health check...
✓ Server health check passed: {"status":"healthy"}
Starting load test...

=== Starting Warmup Phase ===
=== Warmup Phase Complete ===
OK=32000 failed=0
timeSec=30.90
throughput msg/s=1035.72
Warmup connections remain open for reuse.
```

### Main Phase
```
=== Starting Main Phase ===

All workers ready. Generating main phase messages and sending them...

=== Main Phase Results ===
OK=500000 failed=0
timeSec=36.03
throughput msg/s=13875.80
connections=560
reconnections=0
connectionFailures=0
deadWorkers=0
warmupConnectionsReused=32
```

---

## 🔍 Understanding the Metrics

| Metric | Description |
|--------|-------------|
| `OK` | Successfully sent and acknowledged messages |
| `failed` | Messages that failed after 5 retry attempts |
| `timeSec` | Total runtime in seconds |
| `throughput msg/s` | Messages per second |
| `connections` | Total successful WebSocket connections |
| `reconnections` | Times connections were re-established during sending |
| `connectionFailures` | Failed connection attempts (before success) |
| `deadWorkers` | Workers that never connected |
| `warmupConnectionsReused` | Reused warmup connections count |

---

## 🐛 Troubleshooting

### Server Not Reachable
```
✗ Server health check failed: Connection refused
Server is not healthy. Aborting load test.
```
**Solution:** Ensure server is running and accessible at the configured URL.

### Connection Timeouts
```
⚠️  Connection attempt 1/5 failed for ws://.../chat/5, retrying in 50ms...
```
**Solution:** 
- Check server capacity (may be overwhelmed)
- Reduce `connPerRoom`

### Out of Memory
```
java.lang.OutOfMemoryError: Java heap space
```
**Solution:**
```bash
java -Xmx4G -jar target/chatflow-client-part1-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 📁 Project Structure
```
client-part1/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── edu/northeastern/cs6650/client/
                ├── LoadTestClient.java          # Entry point
                ├── generator/
                │   └── MainPhaseMessageGenerator.java
                ├── loadtest/
                │   └── LoadTestRunner.java      # Test orchestrator
                ├── model/
                │   ├── ChatMessage.java
                │   └── MessageType.java
                ├── util/
                │   ├── MessageFactory.java
                │   └── RandomGenerator.java
                └── ws/
                    └── ConnectionWorker.java    # WebSocket worker
```

---

## 🔗 Related Documentation

- [Main Project README](../README.md)
- [Client Part 2 (Enhanced)](../client-part2/README.md)
- [Test Results](../results/)

---

## 📝 Notes

- This is the **basic** implementation without per-message metrics
- For detailed latency analysis, see [client-part2](../client-part2/)
- Warmup phase is crucial for accurate main phase measurements
- Connection failures are normal and handled with retries