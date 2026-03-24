# ChatFlow Consumer (v3)

This module contains the **RabbitMQ consumer application** for **CS6650 Assignment 3**.
The consumer reads messages from room queues, broadcasts them to WebSocket clients via Redis Pub/Sub, and persists them to PostgreSQL using an asynchronous batch writer.

---

## Features

- Configurable thread pool consuming from 20 room queues in parallel
- Round-robin queue assignment across threads
- Competing consumer support when thread count exceeds room count
- Per-thread dedicated RabbitMQ channel with configurable prefetch
- Manual message acknowledgment (ack after successful Redis publish)
- Retry with exponential backoff (up to 3 attempts on broadcast failure)
- Duplicate message detection (per-thread messageId cache, 60s TTL)
- Broadcast Dead Letter Queue (BroadcastDLQ) for messages that exhaust inline retries
- Asynchronous batch DB persistence via `BatchWriter` (PostgreSQL)
- Per-consumer pipeline metrics (received, published, nacked, duplicates, processing latency)
- Health check REST endpoints (`/health`, `/health/stats`)
- Automatic RabbitMQ connection recovery
- Deployed on AWS EC2

---

## Technology Stack

- **Java 21**
- **Spring Boot**
- **RabbitMQ** (AMQP via `amqp-client`)
- **Redis** (via `spring-data-redis`)
- **PostgreSQL** (via `spring-data-jpa` + HikariCP)
- **Jackson**
- **JUnit 5 + Mockito**

---

## How It Works

```
RabbitMQ queues (room.1 … room.20)
        │
        │  round-robin assignment
        ▼
ConsumerThreadPool  (N threads, one channel each)
        │
        │  each thread: basicConsume → deserialize → broadcastWithRetry
        ▼
Redis Pub/Sub  (channel: chat:room:{roomId})
        │
        │  all subscribed server-v3 instances receive the message
        ▼
WebSocket broadcast to all sessions in that room
        │
        │  (parallel path, after successful broadcast)
        ▼
BatchWriter  (in-memory buffer → periodic flush to PostgreSQL)
```

Messages that fail all inline Redis publish attempts are handed off to `BroadcastDLQ`
for background retry, preserving at-least-once delivery without blocking the consumer thread.

---

## Queue Assignment Model

Queues are distributed round-robin at startup. The behavior scales with thread count:

| Thread count | Rooms per thread | Notes |
|---|---|---|
| 10 | 2 | Sequential processing within each room |
| 20 | 1 | Maximum parallelism, ordering guaranteed per room |
| 40 | Competing (2 threads/room) | Higher throughput, no ordering guarantee |
| 80 | Competing (4 threads/room) | Maximum throughput |

When thread count exceeds room count, extra threads mirror existing room assignments as **competing consumers** — RabbitMQ delivers each message to whichever thread is free first.

---

## REST API

### Health Check
```bash
GET /health
```
```json
{
  "status": "UP",
  "timestamp": "2026-03-01T12:00:00Z"
}
```

### Stats
```bash
GET /health/stats
```
Returns real-time pipeline metrics:
```json
{
  "status": "UP",
  "consumer.received": 500000,
  "consumer.published": 499998,
  "consumer.nacked": 0,
  "consumer.duplicates": 2,
  "consumer.broadcastDlqSize": 0,
  "consumer.avgProcessingLatencyMs": 3,
  "db.written": 500000,
  "db.dropped": 0,
  "db.bufferSize": 0,
  "db.flushCount": 2425,
  "db.avgFlushLatencyMs": 19,
  "db.writtenPerSec": 1278
}
```

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.9+
- RabbitMQ, Redis, and PostgreSQL running (see `deployment/docker-compose.yml`)

### Build and Run
```bash
mvn clean package
java -jar target/consumer.jar
```
Default port: **8081**

### Configuration
```properties
# application.properties
server.port=8081

rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=admin
rabbitmq.password=admin123

spring.data.redis.host=localhost
spring.data.redis.port=6379

spring.datasource.url=jdbc:postgresql://localhost:5432/chatflow
spring.datasource.username=chatflow
spring.datasource.password=chatflow123

# Tuning
consumer.thread.count=20
consumer.prefetch.count=10
db.batch.size=100
db.flush.interval.ms=500
```

---

## Batch Writer Tuning

`BatchWriter` buffers incoming messages in memory and flushes to PostgreSQL when either the
buffer reaches `batchSize` messages **or** `flushIntervalMs` milliseconds have elapsed —
whichever fires first.

### Why batching matters

Each PostgreSQL flush has a fixed round-trip overhead regardless of how many rows are
inserted. Larger batches amortize that cost over more rows. The key comparison metric is:

**Total DB work = flushCount × avgFlushLatencyMs**

Lower total work = fewer round-trips and less cumulative time spent waiting on the database.

### Tuning results (500k messages, 256 workers, 0 drops across all runs)

| Run | batchSize | flushIntervalMs | throughput msg/s | duration (s) | flushCount | avgFlushMs | DB written/s | total DB work |
|-----|-----------|-----------------|-----------------|--------------|------------|------------|--------------|---------------|
| A1  | 100       | 100             | 2,064           | 242          | 6,338      | 6 ms       | 1,728/s      | 38,028 ms     |
| **A2** | **100** | **500**        | **2,183**       | **229**      | 5,851      | 7 ms       | **1,888/s**  | 40,957 ms     |
| A3  | 500       | 500             | 2,130           | 235          | 1,407      | 20 ms      | 1,686/s      | 28,140 ms     |
| A4  | 1,000     | 1,000           | 1,981           | 252          | 893        | 34 ms      | 1,798/s      | 30,362 ms     |
| A5  | 5,000     | 1,000           | 2,111           | 237          | 206        | 140 ms     | 1,801/s      | 28,840 ms     |

Total DB work = `flushCount × avgFlushLatencyMs` — measures cumulative time spent in DB round-trips.

### Chosen config: `batchSize=100`, `flushIntervalMs=500`

**A2 (batch=100, flush=500ms)** is the winner:

- **Highest end-to-end throughput** (2,183 msg/s) and shortest test duration (229s)
- **Highest DB write rate** (1,888 written/s) — the consumer keeps up with the pipeline without falling behind
- **Lowest avg flush latency** (7ms) — small batches complete fast, keeping the buffer clear and avoiding backpressure
- **Why A2 beats A1** (same batch size, longer flush interval): 100ms interval causes many partial-batch flushes; 500ms allows the buffer to accumulate a full batch before flushing, reducing wasted round-trips
- **Why A2 beats A3+**: larger batches (500–5,000) reduce flush count but each flush takes 20–140ms; at A5's 140ms avg flush, a single slow flush stalls the writer thread and creates buffer pressure. The throughput gains don't compensate
- **A4 is the worst** (1,981 msg/s, 252s) — 1,000-row batches at 1,000ms flush interval introduces the most backpressure without the amortization benefit of A5's larger batch

---

## Testing

### Unit Tests
```bash
mvn test
```

Test coverage:
- `ConsumerThreadPool` — queue assignment logic for all thread count scenarios
- `RoomConsumer` — message deserialization, duplicate detection, and Redis publish
- `BatchWriter` — buffering, size-triggered flush, interval-triggered flush
- `MessageRepo` — DB write integration

---

## Deployment (AWS EC2)

The consumer runs on its own dedicated `t3.micro` instance. RabbitMQ and Redis each run on separate dedicated `t3.micro` instances (Docker). The consumer connects to both.

- Instance type: `t3.micro`
- Region: `us-west-2`
- OS: Amazon Linux
- Security Group:
  - TCP **8081** (health check)
  - TCP **22** (SSH)
- Java 21 (Amazon Corretto)
- Application packaged as a standalone Spring Boot JAR
- Service managed using `systemd`
- To change thread count: edit `/etc/systemd/system/consumer.service`, then:

```bash
sudo systemctl daemon-reload
sudo systemctl restart consumer
sudo systemctl status consumer
```

---

## Project Structure

```
consumer-v3/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/edu/northeastern/cs6650/consumer/
    │   │   ├── ConsumerApplication.java
    │   │   ├── config/
    │   │   │   └── RabbitMQConfig.java          # Connection, exchange, queue topology
    │   │   ├── consumer/
    │   │   │   ├── BroadcastDLQ.java            # Dead letter queue for failed broadcasts
    │   │   │   ├── ConsumerThreadPool.java      # Thread pool + round-robin assignment
    │   │   │   └── RoomConsumer.java            # Per-thread consumer logic
    │   │   ├── db/
    │   │   │   ├── BatchWriter.java             # Async batch persistence to PostgreSQL
    │   │   │   ├── DeadLetterQueue.java         # Dead letter queue for failed writes into DB
    │   │   │   └── MessageRepo.java             # Spring Data JPA repository
    │   │   ├── health/
    │   │   │   └── HealthController.java
    │   │   ├── metrics/
    │   │   │   ├── BatchMetrics.java            # Batch writing lantency
    │   │   │   └── ConsumerMetrics.java         # Per-pipeline counters and latency
    │   │   ├── model/
    │   │   │   └── ChatMessage.java
    │   │   └── redis/
    │   │       └── RedisPublisher.java          # Publishes to Redis Pub/Sub
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/edu/northeastern/cs6650/consumer/
            ├── consumer/
            │   ├── BroadvastDLQTest.java            
            │   ├── ConsumerThreadPoolTest.java
            │   └── RoomConsumerTest.java
            └── db/
                ├── BatchWriterTest.java
                └── MessageRepoTest.java
```

---

## Related Documentation

- [Main Project README](../README.md)
- [Server v3](../server-v3/README.md)
- [Deployment](../deployment/README.md)
- [Architecture Document](../doc/architecture.md)

---

## Author
Lelin Zheng
CS6650 – Scalable Distributed Systems
