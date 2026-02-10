# Client Part 2: Performance Analysis

Enhanced WebSocket client with detailed per-message metrics, statistical analysis, and CSV export for visualization.

---

## 📋 Features

### All Part 1 Features
- Warmup phase: 32 threads × 1000 messages
- Main phase: 560 threads × 500K messages
- Basic metrics reporting

### Enhanced Metrics (Part 2)
✨ **Per-Message Tracking**
- Individual message latency (ms)
- Timestamp for each message
- Status code (OK, FAILED, NO_CONNECTION)
- Room ID and message type

✨ **Statistical Analysis**
- Mean response time
- Median response time
- 95th percentile (p95)
- 99th percentile (p99)
- Min/max response times

✨ **Performance Insights**
- Throughput per room
- Message type distribution
- Time-series data (10-second buckets)

✨ **Data Export**
- CSV format for easy analysis
- Compatible with Excel, Python, R
- Ready for visualization

---

## 🏗️ Architecture

### Enhanced Threading Model
```
Main Thread
  ├── Generator Thread
  ├── Worker Thread Pool (560 threads)
  │   └── Each worker records latency per message
  ├── Metrics Writer Thread  ← NEW!
  │   └── Writes CSV in real-time
  └── Metrics Analyzer  ← NEW!
      └── Calculates statistics
```

### Metrics Collection Flow
```
Worker sends message
  ↓ records timestamp
WebSocket echo received
  ↓ calculates latency
MetricRecord created
  ↓ put in queue
CsvMetricsWriter
  ↓ writes to CSV
MetricsAnalyzer
  ↓ reads CSV
Statistical Summary
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

### Run
```bash
java -jar target/chatflow-client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 📁 Output Files

After running, find these files in `../results/` (repo root):

### 1. main_metrics.csv
Per-message metrics for main phase:
```csv
timestamp,messageType,latencyMs,statusCode,roomId
1707480123456,TEXT,35,OK,1
1707480123457,JOIN,42,OK,5
1707480123458,TEXT,31,OK,12
```

**Location:** `../results/main_metrics.csv`

### 2. warmup_metrics.csv
Per-message metrics for warmup phase

**Location:** `../results/warmup_metrics.csv`

### 3. throughput_10s.csv
Throughput in 10-second buckets:
```csv
bucketStartMillis,count,throughputMsgPerSec
1707480120000,13450,1345.0
1707480130000,13890,1389.0
```

**Location:** `../results/throughput_10s.csv`

---

## 📊 Expected Output

### Console Output
```
=== Starting main phase ===

All workers ready. Generating main phase messages and sending them...

=== Main Phase Results ===
OK=500000 failed=0
timeSec=36.370
throughput msg/s=13742.28
connections=560
reconnections=0
connectionFailures=356
deadWorkers=0
warmupConnectionsReused=32

Load test summary:
Main phase metrics to be aggregated here.

=== Metrics Summary ===
OK=500000 failed=0
durationSec=36.370

=== Latency (OK only, ms) ===
mean=36.317
median=35.000
p95=48.000
p99=63.000
min=15.000 max=293.000

=== Throughput per room (OK only, msg/s) ===
room 1: 753.02
room 2: 759.94
room 3: 755.89
...
room 20: 710.34

=== Message type distribution (all statuses) ===
TEXT: 450245
JOIN: 24877
LEAVE: 24878

Throughput buckets CSV: ../results/throughput_10s.csv
```

---

## 📈 Visualizing Results

### Using Excel
1. Open `../results/throughput_10s.csv`
2. Create a line chart with:
   - X-axis: `bucketStartMillis`
   - Y-axis: `throughputMsgPerSec`

### Using Python
```python
import pandas as pd
import matplotlib.pyplot as plt

# Load data from repo root
df = pd.read_csv('../results/main_metrics.csv')

# Plot latency distribution
plt.hist(df[df['statusCode'] == 'OK']['latencyMs'], bins=50)
plt.xlabel('Latency (ms)')
plt.ylabel('Frequency')
plt.title('Message Latency Distribution')
plt.show()

# Plot throughput over time
buckets = pd.read_csv('../results/throughput_10s.csv')
plt.plot(buckets['bucketStartMillis'], buckets['throughputMsgPerSec'])
plt.xlabel('Time')
plt.ylabel('Throughput (msg/s)')
plt.title('Throughput Over Time')
plt.show()
```

### Generate Charts for Submission
```bash
# From repo root
cd results
python generate_charts.py  # Create visualizations
```

---

## 🧪 Testing

### Run Unit Tests
```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=MetricsAnalyzerTest
```

### Test Coverage
- `MessageFactory`: Message generation logic
- `MetricsAnalyzer`: Statistical calculations
- `MetricRecord`: Data structure validation
- `ChatMessage`: Poison pill detection
- `RandomGenerator`: Range validation

---

## ⚙️ Configuration

### Connection Per Room
```java
// In LoadTestRunner.java
int connPerRoom = 28; // must be >= 2 in order to reuse the warmup connections
```


### Output Directory Configuration

**Current setup** (writes to `../results/`):
```java
// In LoadTestRunner.java
Path outDir = Paths.get("..", "results");  // Writes to repo root
Path csvPath = outDir.resolve("main_metrics.csv");
```

### Adjust Queue Sizes
In `LoadTestRunner.java`:
```java
int queueCapacity = 3000;  // Per-worker message queue
BlockingQueue<MetricRecord> metricsQueue = new ArrayBlockingQueue<>(50_000);
```

---

## 🔍 Understanding Detailed Metrics

### Status Codes
| Code | Meaning |
|------|---------|
| `OK` | Message successfully sent and acknowledged |
| `FAILED_AFTER_RETRIES` | Failed after 5 retry attempts |
| `NO_CONNECTION` | Worker never connected, message not sent |
| `TIMEOUT` | Echo not received within timeout period |

### Latency Values
- `> 0`: Actual round-trip time in milliseconds
- `-1`: Message failed (check status code)

---

## 📊 Performance Tuning

### If Throughput Too Low
1. Increase or decrease connections per room: `connPerRoom = 35`
2. Reduce queue capacity to decrease memory: `queueCapacity = 2000`
3. Disable console logging for workers


### If Out of Memory
```bash
java -Xmx4G -Xms2G -jar target/chatflow-client-part2-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 📁 Project Structure
```
client-part2/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   └── java/
│   │       └── edu/northeastern/cs6650/client/
│   │           ├── LoadTestClient.java
│   │           ├── generator/
│   │           │   └── MainPhaseMessageGenerator.java
│   │           ├── loadtest/
│   │           │   └── LoadTestRunner.java
│   │           ├── metrics/              ← NEW in Part 2
│   │           │   ├── CsvMetricsWriter.java
│   │           │   ├── MetricRecord.java
│   │           │   └── MetricsAnalyzer.java
│   │           ├── model/
│   │           │   ├── ChatMessage.java
│   │           │   └── MessageType.java
│   │           ├── util/
│   │           │   ├── MessageFactory.java
│   │           │   └── RandomGenerator.java
│   │           └── ws/
│   │               └── ConnectionWorker.java
│   └── test/
│       └── java/
│           └── edu/northeastern/cs6650/client/
│               ├── metrics/
│               │   ├── MetricRecordTest.java
│               │   └── MetricsAnalyzerTest.java
│               ├── model/
│               │   └── ChatMessageTest.java
│               └── util/
│                   ├── MessageFactoryTest.java
│                   └── RandomGeneratorTest.java
└── target/
    └── (compiled artifacts)
```

### Output Files (at repo root)
```
../results/                    ← Repo root level
├── main_metrics.csv          ← Generated by client-part2
├── warmup_metrics.csv        ← Generated by client-part2
├── throughput_10s.csv        ← Generated by client-part2
├── part1_output.txt          ← For submission
├── part2_output.txt          ← For submission
├── ec2_screenshot.png        ← For submission
└── charts/                   ← For submission
    └── throughput_over_time.png
```

---

## 🔗 Related Documentation

- [Main Project README](../README.md)
- [Client Part 1 (Basic)](../client-part1/README.md)
- [Test Results & Analysis](../results/README.md)
- [Server Documentation](../server/README.md)
