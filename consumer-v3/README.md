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
batch.writer.batch.size=500
batch.writer.flush.interval.ms=500
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

### Tuning results (500k messages, 0 drops)

| batchSize | flushIntervalMs | flushCount | avgFlushMs | written/s | total DB work |
|-----------|-----------------|------------|------------|-----------|---------------|
| 100       | 500             | 9,787      | 7 ms       | 1,222/s   | 68,509 ms     |
| **500**   | **500**         | **2,425**  | **19 ms**  | **1,278/s** | **46,075 ms** |
| 1,000     | 1,000           | 1,485      | 36 ms      | 1,243/s   | 53,460 ms     |
| 5,000     | 1,000           | 388        | 92 ms      | 1,228/s   | 35,696 ms     |
| 5,000     | 100             | 3,884      | 13 ms      | 1,225/s   | 50,492 ms     |

### Chosen config: `batchSize=500`, `flushIntervalMs=500`

**batch=500/500ms** achieves the best balance:

- **Lowest total DB work among practical configs** (46,075 ms) — fewer round-trips than
  batch=100 (which flushes too frequently) and lower latency variance than batch=1000+
- **Highest write throughput** (1,278 msg/s) — larger batches amortize round-trip cost
  without introducing the high per-flush latency seen at batch=1000+ (36–92 ms)
- **Predictable tail latency** — 19 ms avg flush is low enough that no individual flush
  creates a meaningful backpressure bubble on the in-memory buffer
- **Avoids the batch=5000 risk** — batch=5000/1000ms technically has lower total work, but
  bursts of 5k rows per flush create uneven DB load spikes and make the 1s interval a
  hard dependency; one slow flush can stall the buffer

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
