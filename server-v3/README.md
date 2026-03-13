# ChatFlow WebSocket Server v2

This module contains the **WebSocket server implementation** for **CS6650 Assignment 2**.
The server accepts WebSocket connections, validates and deduplicates incoming messages, publishes them to RabbitMQ for distribution, and broadcasts messages back to room members via Redis Pub/Sub.

This is an evolution of the Assignment 1 server, adding queue-based message distribution and horizontal scalability behind an AWS Application Load Balancer.

---

## Features

- WebSocket endpoint `/chat` — room is in the message body, not the URI
- JSON message validation with detailed error responses
- Message deduplication via Redis (drops replayed `messageId`)
- RabbitMQ publish with pooled channels (`ChannelPool`)
- Circuit breaker protecting RabbitMQ publish operations
- Redis Pub/Sub subscriber for cross-instance broadcast
- Room session management (`RoomManager`)
- Health check REST endpoint
- Deployed on AWS EC2 behind an Application Load Balancer

---

## Technology Stack

- **Java 21**
- **Spring Boot**
- **Spring WebSocket**
- **RabbitMQ** (AMQP via `amqp-client`)
- **Redis** (via `spring-data-redis`)
- **Jackson**
- **JUnit 5 + Mockito**
- **JaCoCo**

---

## WebSocket API

### Endpoint
```
ws://<host>/chat
```
- All message types (JOIN, TEXT, LEAVE) are sent to the same endpoint.
- The `roomId` and `messageId` fields are required in the JSON body.
- On each message, the server moves the sender's session into the specified room, then publishes the message to RabbitMQ.

---

### Message Format

Incoming messages must be valid JSON:

```json
{
  "messageId": "uuid-string",
  "userId": "string (1-100000)",
  "username": "string (3-20 chars)",
  "message": "string (1-500 chars)",
  "timestamp": "ISO-8601 timestamp",
  "messageType": "TEXT | JOIN | LEAVE",
  "roomId": "string (1-20)"
}
```

---

### Validation Rules

- `messageId` must be present (used for deduplication)
- `userId` must be numeric and between 1–100000
- `username` must be 3–20 alphanumeric characters (including underscore)
- `message` must be 1–500 characters for TEXT messages
- `timestamp` must be valid ISO-8601
- `messageType` must be present and valid
- `roomId` must be present and between 1–20

---

### Server Responses

#### Validation Error
```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Message validation failed",
  "details": ["roomId is required"]
}
```

#### Circuit Breaker Open
```json
{
  "errorCode": "SERVICE_UNAVAILABLE",
  "message": "Message queue is currently unavailable, please try again later",
  "details": []
}
```

#### Publish Failed
```json
{
  "errorCode": "PUBLISH_FAILED",
  "message": "Failed to deliver message, please try again",
  "details": ["Connection reset"]
}
```

#### Broadcast (echo from Redis)
The message is echoed back as the original JSON payload to all WebSocket sessions currently in the same room, including the sender.

---

### REST API

#### Health Check
```bash
GET /health
```
```json
{
  "status": "UP",
  "server": "server-1",
  "timestamp": "2026-03-01T12:00:00Z"
}
```

---

## Circuit Breaker

A custom thread-safe circuit breaker wraps every RabbitMQ publish call.

| Parameter | Value |
|---|---|
| Failure threshold | 5 consecutive failures |
| Recovery timeout | 30 000 ms |
| State transitions | CLOSED → OPEN → HALF_OPEN → CLOSED |

- **CLOSED** — normal operation, all publishes go through
- **OPEN** — RabbitMQ considered unavailable; client receives `SERVICE_UNAVAILABLE`
- **HALF_OPEN** — one probe allowed; success resets to CLOSED, failure restarts the timeout

---

## Message Deduplication

`MessageDeduplicator` stores each `messageId` in Redis with a short TTL. If a message with the same ID arrives again (e.g. client retry after a network hiccup), it is silently dropped before publishing.

---

## Room Management

`RoomManager` maintains a `ConcurrentHashMap` of `roomId → Set<WebSocketSession>`.
On each incoming message, the sender's session is moved to the room specified in the message. When a message arrives via Redis Pub/Sub, it is broadcast to all sessions currently in that room across the local instance.

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.9+
- RabbitMQ and Redis running (see `deployment/docker-compose.yml`)

### Build and Run
```bash
mvn clean package
java -jar target/chat-server-v2.jar
```
Default port: **8080**

### Configuration
```properties
# application.properties
rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=admin
rabbitmq.password=admin123

spring.data.redis.host=localhost
spring.data.redis.port=6379
```

---

## Testing

### Unit Tests
```bash
mvn test
```

### Code Coverage
```bash
target/site/jacoco/index.html
```

---

## Manual Testing

### Using wscat
```bash
wscat -c ws://localhost:8080/chat
```
Example message:
```json
{"messageId":"abc-123","userId":"1","username":"user1","message":"hello","timestamp":"2026-01-27T02:30:00Z","messageType":"TEXT","roomId":"5"}
```

---

## Deployment (AWS EC2)

- Instance type: `t3.micro`
- Region: `us-west-2`
- OS: Amazon Linux
- Security Group:
  - TCP **8080** (application / ALB target)
  - TCP **22** (SSH)
- Java 21 (Amazon Corretto)
- Application packaged as a standalone Spring Boot JAR
- Service managed using `systemd`
- 1, 2, or 4 instances registered in the ALB target group

---

## Project Structure

```
server-v2/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/edu/northeastern/cs6650/chat_server/
    │       ├── ChatServerApplication.java
    │       ├── circuitbreaker/
    │       │   └── CircuitBreaker.java          # 3-state circuit breaker
    │       ├── config/
    │       │   ├── ChannelPool.java             # Pooled RabbitMQ channels
    │       │   ├── RabbitMQConfig.java          # Exchange + queue declaration
    │       │   ├── RedisConfig.java             # Pub/Sub listener setup
    │       │   └── WebSocketConfig.java
    │       ├── controller/
    │       │   └── HealthController.java
    │       ├── dedup/
    │       │   └── MessageDeduplicator.java     # Redis-backed deduplication
    │       ├── model/
    │       │   ├── ClientMessage.java
    │       │   ├── ErrorResponse.java
    │       │   ├── Messagetype.java
    │       │   └── QueueMessage.java
    │       ├── redis/
    │       │   └── RedisSubscriber.java         # Broadcasts to room sessions
    │       ├── validation/
    │       │   └── MessageValidator.java
    │       └── websocket/
    │           ├── ChatWebSocketHandler.java    # Core message handling
    │           └── RoomManager.java             # Session-to-room mapping
    └── test/
        └── java/edu/northeastern/cs6650/chat_server/
            └── circuitbreaker/
                └── CircuitBreakerTest.java
```

---

## Related Documentation

- [Main Project README](../README.md)
- [Consumer Application](../consumer/README.md)
- [Deployment](../deployment/README.md)
- [Client v2](../client-v2/README.md)
- [Architecture Document](../doc/architecture.md)

---

## Author
Lelin Zheng
CS6650 – Scalable Distributed Systems
