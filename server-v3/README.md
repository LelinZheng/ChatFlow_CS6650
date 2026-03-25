# ChatFlow WebSocket Server (v3)

This module contains the **WebSocket server implementation** for **CS6650 Assignment 3**.
The server accepts WebSocket connections, validates and deduplicates incoming messages, publishes them to RabbitMQ for distribution, and broadcasts messages back to room members via Redis Pub/Sub.

In addition to the Assignment 2 capabilities, v3 adds a **REST metrics API** backed by PostgreSQL — enabling post-load-test analytics over all persisted messages.

---

## Features

- WebSocket endpoint `/chat` — room is in the message body, not the URI
- JSON message validation with detailed error responses
- Message deduplication via Redis (drops replayed `messageId`)
- RabbitMQ publish with pooled channels (`ChannelPool`)
- Circuit breaker protecting RabbitMQ publish operations
- Redis Pub/Sub subscriber for cross-instance broadcast
- Room session management (`RoomManager`)
- Thread-safe WebSocket session writes (`synchronized` on each session)
- Health check REST endpoint
- **Analytics REST endpoint** (`/api/metrics`) — queries persisted messages in PostgreSQL
- Deployed on AWS EC2 behind an Application Load Balancer

---

## Technology Stack

- **Java 21**
- **Spring Boot**
- **Spring WebSocket**
- **RabbitMQ** (AMQP via `amqp-client`)
- **Redis** (via `spring-data-redis`)
- **PostgreSQL** (via `spring-data-jpa` — read-only queries against consumer-written data)
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

## REST API

### Health Check
```bash
GET /health
```
```json
{
  "status": "UP",
  "serverId": "server-1",
  "timestamp": "2026-03-01T12:00:00Z"
}
```

### Metrics
```bash
GET /api/metrics?roomId=5&userId=42&startTime=2026-03-01T00:00:00Z&endTime=2026-03-01T01:00:00Z&topN=10&sampleSize=1000
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `roomId` | string | most active room | Room ID for the room time-range query |
| `userId` | string | most active user | User ID for history and user-rooms queries |
| `startTime` | ISO-8601 | earliest message | Start of time window |
| `endTime` | ISO-8601 | latest message | End of time window |
| `topN` | int | 10 | Result count for top-N analytics queries |
| `sampleSize` | int | 1000 | Max rows returned per core query list |

All parameters are optional. If omitted, the endpoint auto-selects the most active room/user and the full time range from the data. Returns `{"totalMessages": 0, "message": "No messages in database yet."}` if the table is empty.

```json
{
  "totalMessages": 500000,
  "coreQueryInputs": {
    "roomId": "5",
    "userId": "42",
    "startTime": "2026-03-01T00:00:00Z",
    "endTime": "2026-03-01T01:00:00Z"
  },
  "coreQueries": {
    "roomMessagesInTimeRange": [...],
    "userMessageHistory": [...],
    "activeUserCount": 1000,
    "userRoomsParticipated": [...]
  },
  "analytics": {
    "messagesPerMinute": [...],
    "mostActiveUsers": [...],
    "mostActiveRooms": [...],
    "userParticipationPatterns": [...]
  },
  "queryTimingsMs": {
    "roomMessagesInTimeRangeMs": 131,
    "userMessageHistoryMs": 19,
    "activeUserCountMs": 126,
    "userRoomsParticipatedMs": 3,
    "messagesPerMinuteMs": 174,
    "mostActiveUsersMs": 153,
    "mostActiveRoomsMs": 114,
    "userParticipationPatternsMs": 645
  }
}
```

---

## Circuit Breaker

A custom thread-safe circuit breaker wraps every RabbitMQ publish call.

| Parameter | Value |
|---|---|
| Failure threshold | 5 consecutive failures |
| Recovery timeout | 30,000 ms |
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

All `session.sendMessage()` calls are guarded with `synchronized(session)` to prevent
`TEXT_PARTIAL_WRITING` exceptions when multiple Redis listener threads concurrently write
to the same session.

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.9+
- RabbitMQ, Redis, and PostgreSQL running (see `deployment/docker-compose.yml`)

### Build and Run
```bash
mvn clean package
java -jar target/chat-server-v3.jar
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

spring.datasource.url=jdbc:postgresql://localhost:5432/chatflow
spring.datasource.username=chatflow
spring.datasource.password=chatflow123
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

Test coverage:
- `CircuitBreakerTest` — state transitions, failure threshold, recovery timeout
- `ChannelPoolTest` — borrow/return semantics, pool exhaustion
- `RabbitMQConfigTest` — exchange/queue declaration, topology args
- `MetricsControllerTest` — query parameter defaulting, response shape
- `QueueMessageTest` — model construction
- `RedisSubscriberTest` — message deserialization and broadcast delegation
- `MessageValidatorTest` — all validation rules and edge cases
- `ChatWebSocketHandlerTest` — message flow, error paths, circuit breaker integration
- `RoomManagerTest` — session add/remove, broadcast, dead session cleanup

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
server-v3/
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
    │       ├── metrics/
    │       │   ├── MetricsController.java       # GET /api/metrics analytics endpoint
    │       │   └── MetricsRepository.java       # PostgreSQL query methods
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
            ├── ChatServerApplicationTests.java
            ├── circuitbreaker/
            │   └── CircuitBreakerTest.java
            ├── config/
            │   ├── ChannelPoolTest.java
            │   └── RabbitMQConfigTest.java
            ├── metrics/
            │   └── MetricsControllerTest.java
            ├── model/
            │   └── QueueMessageTest.java
            ├── redis/
            │   └── RedisSubscriberTest.java
            ├── validation/
            │   └── MessageValidatorTest.java
            └── websocket/
                ├── ChatWebSocketHandlerTest.java
                └── RoomManagerTest.java
```

---

## Related Documentation

- [Main Project README](../README.md)
- [Consumer v3](../consumer-v3/README.md)
- [Deployment](../deployment/README.md)
- [Client v3](../client-v3/README.md)
- [Architecture Document](../doc/architecture.md)

---

## Author
Lelin Zheng
CS6650 – Scalable Distributed Systems
