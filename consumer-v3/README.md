# ChatFlow Consumer

This module contains the **RabbitMQ consumer application** for **CS6650 Assignment 2**.
The consumer reads messages from room queues, processes them, and publishes them to Redis Pub/Sub so that every server-v2 instance can broadcast them to the correct WebSocket room.

---

## Features

- Configurable thread pool consuming from 20 room queues in parallel
- Round-robin queue assignment across threads
- Competing consumer support when thread count exceeds room count
- Per-thread dedicated RabbitMQ channel with configurable prefetch
- Manual message acknowledgment (ack after successful Redis publish)
- Automatic RabbitMQ connection recovery
- Health check REST endpoint
- Deployed on AWS EC2

---

## Technology Stack

- **Java 21**
- **Spring Boot**
- **RabbitMQ** (AMQP via `amqp-client`)
- **Redis** (via `spring-data-redis`)
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
        │  each thread: basicConsume → deserialize → RedisPublisher
        ▼
Redis Pub/Sub  (channel: room:{roomId})
        │
        │  all subscribed server-v2 instances receive the message
        ▼
WebSocket broadcast to all sessions in that room
```

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

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.9+
- RabbitMQ and Redis running (see `deployment/docker-compose.yml`)

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

# Tuning
consumer.thread.count=20
consumer.prefetch.count=10
```

---

## Testing

### Unit Tests
```bash
mvn test
```

Test coverage:
- `ConsumerThreadPool` — queue assignment logic for all thread count scenarios
- `RoomConsumer` — message deserialization and Redis publish

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
consumer/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/edu/northeastern/cs6650/consumer/
    │   │   ├── ConsumerApplication.java
    │   │   ├── config/
    │   │   │   └── RabbitMQConfig.java          # Connection, exchange, queue topology
    │   │   ├── consumer/
    │   │   │   ├── ConsumerThreadPool.java      # Thread pool + round-robin assignment
    │   │   │   └── RoomConsumer.java            # Per-thread consumer logic
    │   │   ├── health/
    │   │   │   └── HealthController.java
    │   │   ├── model/
    │   │   │   └── ChatMessage.java
    │   │   └── redis/
    │   │       └── RedisPublisher.java          # Publishes to Redis Pub/Sub
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/edu/northeastern/cs6650/consumer/
            ├── consumer/
            │   ├── ConsumerThreadPoolTest.java
            │   └── RoomConsumerTest.java
```

---

## Related Documentation

- [Main Project README](../README.md)
- [Server v2](../server-v2/README.md)
- [Deployment](../deployment/README.md)
- [Architecture Document](../doc/architecture.md)

---

## Author
Lelin Zheng
CS6650 – Scalable Distributed Systems
