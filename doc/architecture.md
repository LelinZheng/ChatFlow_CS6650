# Architecture Document — ChatFlow CS6650 Assignment 2

---

## 1. System Architecture Diagram

```mermaid
graph TD
    C["Client-v2 (up to 512 WebSocket workers)"]
    ALB["AWS Application Load Balancer (port 80)"]

    subgraph SRV ["Server-v2 Instances x 1 / 2 / 4  —  t3.micro"]
        S1["Server-v2 #1"]
        S2["Server-v2 #2"]
        SN["Server-v2 #N"]
    end

    subgraph BROKER ["RabbitMQ  —  dedicated t3.micro (Docker)"]
        RMQ["RabbitMQ 3 — chat.exchange (topic, durable) — 20 queues"]
    end

    subgraph CONSUMER ["Consumer Application  —  dedicated t3.micro"]
        CP["ConsumerThreadPool (10 / 20 / 40 / 80 threads)"]
    end

    subgraph CACHE ["Redis  —  dedicated t3.micro (Docker)"]
        Redis["Redis 7 — Pub/Sub channels room:1 to room:20"]
    end

    C --> ALB
    ALB --> S1
    ALB --> S2
    ALB --> SN
    S1 -->|"basicPublish room.{id}"| RMQ
    S2 -->|"basicPublish room.{id}"| RMQ
    SN -->|"basicPublish room.{id}"| RMQ
    RMQ -->|"deliver from room queues"| CP
    CP -->|"PUBLISH room:{id}"| Redis
    Redis -->|"SUBSCRIBE room:{id}"| S1
    Redis -->|"SUBSCRIBE room:{id}"| S2
    Redis -->|"SUBSCRIBE room:{id}"| SN
```

---

## 2. Message Flow Sequence Diagram

```mermaid
sequenceDiagram
    participant C  as Client-v2
    participant ALB as ALB
    participant S  as Server-v2
    participant CB as CircuitBreaker
    participant RMQ as RabbitMQ
    participant CON as Consumer Thread
    participant R  as Redis
    participant S2 as Other Server-v2 Instances

    C->>ALB: WebSocket frame (JSON message)
    ALB->>S: forward frame (same server for lifetime of connection)

    S->>S: parse JSON & validate fields
    S->>S: deduplication check (messageId)
    S->>S: addSession(roomId, session)

    S->>CB: allowRequest()?
    alt circuit OPEN
        CB-->>S: false
        S-->>C: ERROR SERVICE_UNAVAILABLE
    else circuit CLOSED / HALF_OPEN probe
        CB-->>S: true
        S->>RMQ: basicPublish(chat.exchange, room.{id}, PERSISTENT)
        alt publish success
            RMQ-->>S: ack
            S->>CB: recordSuccess()
        else publish failure
            RMQ-->>S: exception
            S->>CB: recordFailure()
            S-->>C: ERROR PUBLISH_FAILED
        end
    end

    RMQ->>CON: deliver message from room.{id} queue
    CON->>R: PUBLISH room:{id} (JSON payload)
    CON->>RMQ: basicAck

    R->>S: onMessage(room:{id})
    R->>S2: onMessage(room:{id})
    S->>C: broadcast TextMessage to all sessions in room
    S2->>C: broadcast TextMessage to all sessions in room
```

---

## 3. Queue Topology Design

```mermaid
%%{init: {"flowchart": {"subGraphTitleMargin": {"top": 5, "bottom": 30}}}}%%
graph LR
    PUB["Server-v2 basicPublish (routingKey: room.N, delivery: PERSISTENT)"]

    subgraph EXCHANGE ["Exchange"]
        EX["chat.exchange (topic, durable)"]
    end

    subgraph QUEUES ["Room Queues (durable, TTL 60s, max 10000 msgs)"]
        Q1["room.1"]
        Q2["room.2"]
        Q3["room.3"]
        QD["..."]
        Q20["room.20"]
    end

    PUB --> EX
    EX -->|"room.1"| Q1
    EX -->|"room.2"| Q2
    EX -->|"room.3"| Q3
    EX -->|"room.N"| QD
    EX -->|"room.20"| Q20
```

**Queue settings per room:**

| Setting | Value |
|---|---|
| Durability | Durable (survives broker restart) |
| x-message-ttl | 60 000 ms |
| x-max-length | 10 000 messages |
| Binding | 1 binding per queue, exact routing key `room.N` |

---

## 4. Consumer Threading Model

```mermaid
%%{init: {"flowchart": {"subGraphTitleMargin": {"top": 5, "bottom": 30}}}}%%
graph TD
    TC["Single TCP Connection (automatic recovery)"]

    subgraph Pool ["ConsumerThreadPool — N threads (10 / 20 / 40 / 80)"]
        direction TB
        T1["Thread 0 — room.1, room.11"]
        T2["Thread 1 — room.2, room.12"]
        T3["Thread 2 — room.3, room.13"]
        TD["..."]
        TN["Thread N-1 — room.N to room.20"]
    end

    TC --> T1
    TC --> T2
    TC --> T3
    TC --> TD
    TC --> TN

    T1 -->|"PUBLISH room:1"| R["Redis Pub/Sub"]
    T2 -->|"PUBLISH room:2"| R
    T3 -->|"PUBLISH room:3"| R
    TN -->|"PUBLISH room:N"| R
```

**Queue-to-thread assignment (round-robin):**

| Thread count | Rooms per thread | Notes |
|---|---|---|
| 10 | 2 | Sequential processing within each room |
| 20 | 1 | Maximum parallelism, ordering guaranteed per room |
| 40 | Competing (2 threads/room) | Higher throughput, no ordering guarantee |
| 80 | Competing (4 threads/room) | Maximum throughput |

---

## 5. Load Balancing Configuration

```mermaid
graph TD
    C["Client-v2 Workers (persistent WebSocket connections)"]

    subgraph AWS
        ALB["Application Load Balancer (port 80, sticky sessions enabled, health check: GET /health)"]

        subgraph TG ["Target Group"]
            S1["Server-v2 #1  t3.micro"]
            S2["Server-v2 #2  t3.micro"]
            S3["Server-v2 #3  t3.micro"]
            S4["Server-v2 #4  t3.micro"]
        end
    end

    C -->|"initial connection"| ALB
    ALB -->|"pinned for connection lifetime"| S1
    ALB -->|"pinned for connection lifetime"| S2
    ALB -->|"pinned for connection lifetime"| S3
    ALB -->|"pinned for connection lifetime"| S4
```

**Stickiness design:**

WebSocket connections have two layers of stickiness:

1. **TCP-level (inherent):** Once a WebSocket handshake completes to Server #1, every subsequent frame travels over that same TCP connection — the ALB never re-routes it. This is the primary form of stickiness in this system.

2. **Cookie-based (ALB sticky sessions):** If a worker reconnects after a failure, the ALB sticky session cookie ensures the new connection lands on the same server instance as before, preserving any server-side session state.

**Per-user connection model:** Each `ConnectionWorker` holds one persistent WebSocket connection for the entire test. Multiple users share that connection by sending messages with different `userId` and `roomId` fields. This avoids connection churn while still distributing messages across rooms — the server moves the session to the room specified in each message body, so no reconnection is needed when switching rooms.

---

## 6. Circuit Breaker Pattern

```mermaid
stateDiagram-v2
    [*] --> CLOSED

    CLOSED --> CLOSED   : publish success, reset failure counter
    CLOSED --> OPEN     : 5 consecutive publish failures

    OPEN --> OPEN       : recovery timeout not elapsed, reject immediately
    OPEN --> HALF_OPEN  : 30s elapsed, single probe thread allowed

    HALF_OPEN --> CLOSED : probe success
    HALF_OPEN --> OPEN   : probe failure, restart 30s timer
```

**Parameters (ChatWebSocketHandler.java):**

| Parameter | Value |
|---|---|
| Failure threshold | 5 consecutive failures |
| Recovery timeout | 30 000 ms (30 seconds) |
| Concurrency control | `AtomicReference<State>` + `compareAndSet` — only one thread wins HALF_OPEN probe |
| Client-facing error when OPEN | `SERVICE_UNAVAILABLE` |

---

## 7. Failure Handling Strategies

```mermaid
graph TD
    F1["RabbitMQ unreachable"] --> R1["Circuit Breaker opens after 5 failures. Probe every 30s. Client gets SERVICE_UNAVAILABLE"]
    F2["Server-v2 instance crash"] --> R2["ALB health check removes instance. Clients reconnect with retry. Cookie routes back to same server"]
    F3["Redis unreachable"] --> R3["Consumer acks message but cannot broadcast. Graceful degrade, no crash"]
    F4["Duplicate message"] --> R4["MessageDeduplicator checks messageId in Redis. Duplicate dropped before publish"]
    F5["Consumer crash"] --> R5["Automatic RabbitMQ recovery. Messages stay in durable queue until consumer restarts"]
```

**Summary table:**

| Failure | Detection | Recovery | Message loss? |
|---|---|---|---|
| RabbitMQ down | publish exception | Circuit breaker, client retries up to 5x | No |
| Server crash | ALB health check fails | Instance removed, clients reconnect | In-flight WS messages lost |
| Redis down | publish exception in consumer | Graceful degrade, message acked not broadcast | Yes — broadcast lost |
| Duplicate message | Redis-backed deduplicator | Silent drop before RabbitMQ publish | N/A |
| Consumer crash | RabbitMQ unacked messages requeued | Auto-recovery, messages redelivered | No — durable queue |
