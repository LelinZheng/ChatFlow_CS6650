# ChatFlow – CS6650 Scalable Distributed Systems

This repository contains the implementation for **CS6650 Assignments 1 & 2**, building a **WebSocket-based distributed chat system** with progressively more advanced infrastructure across assignments.

---

## Assignment 1 – WebSocket Server & Load Testing

Establishes the foundation with a WebSocket server that validates and echoes messages, and a multithreaded client that generates high-volume load.

### Scope
- WebSocket-based real-time communication
- Message validation and structured error handling
- Echo responses back to the sender
- Health check REST endpoint
- Multithreaded client for load simulation (Parts 1 & 2)
- Performance measurement and analysis

### Subprojects

#### `/server`
WebSocket server with message validation, echo responses, and REST health endpoint.

📖 See [`server/README.md`](server/README.md)

#### `/client-part1`
Multithreaded WebSocket client simulating high-volume messaging with basic throughput metrics.

📖 See [`client-part1/README.md`](client-part1/README.md)

#### `/client-part2`
Extends the client with per-message latency tracking, statistical analysis (mean, p95, p99), and CSV export.

📖 See [`client-part2/README.md`](client-part2/README.md)

### Deployment
Single AWS EC2 `t3.micro` instance in `us-west-2` running the Spring Boot JAR under `systemd`.

---

## Assignment 2 – Distributed Message Queue & Load Balancing

Extends the system with queue-based message distribution, a dedicated consumer service, Redis Pub/Sub for cross-instance fanout, and horizontal scaling behind an AWS Application Load Balancer.

### Scope
- Room-based message routing via RabbitMQ topic exchange (20 queues)
- Dedicated consumer service with configurable thread pool
- Redis Pub/Sub for broadcasting messages across server instances
- Circuit breaker protecting RabbitMQ publish operations
- Message deduplication via Redis
- AWS ALB with 1 / 2 / 4 server instances
- Load test client redesigned for tunable concurrency (64 / 128 / 256 / 512 workers)
- Per-run labeled metrics output and automated graph generation

### Architecture

```
Client-v2 (load test)
    │ WebSocket /chat
    ▼
AWS ALB (port 80)
    │
    ├── Server-v2 #1 ──┐
    ├── Server-v2 #2 ──┤── publish ──► RabbitMQ (chat.exchange, 20 queues)
    └── Server-v2 #N ──┘                    │
         ▲                                  ▼ consume
         │                             Consumer (thread pool)
         │ subscribe                         │
         │                                  ▼ publish
         └──────────────────────────── Redis Pub/Sub
```

Each component runs on a dedicated `t3.micro` EC2 instance. RabbitMQ and Redis each run in Docker on their own instance.

### Subprojects

#### `/server-v2`
Extended WebSocket server. Publishes messages to RabbitMQ, subscribes to Redis Pub/Sub for room broadcast. Includes circuit breaker, message deduplication, and channel pooling.

📖 See [`server-v2/README.md`](server-v2/README.md)

#### `/consumer`
Spring Boot consumer service. Reads from 20 RabbitMQ room queues using a configurable thread pool and publishes to Redis Pub/Sub for server fanout.

📖 See [`consumer/README.md`](consumer/README.md)

#### `/client-v2`
Redesigned load test client. Single shared queue, no warmup phase, seed-phase membership priming, tunable worker count. Output files labeled per run (`summary_256w.txt`, `main_metrics_256w.csv`).

📖 See [`client-v2/README.md`](client-v2/README.md)

#### `/deployment`
Docker Compose for local RabbitMQ + Redis, AWS deployment instructions, ALB configuration, and `systemd` service examples for server-v2 and consumer.

📖 See [`deployment/README.md`](deployment/README.md)

#### `/monitoring`
Python script (`plot_results.py`) that reads collected `summary_*w.txt` and `throughput_10s_*w.csv` files and generates all graphs needed for the report.

📖 See [`monitoring/`](monitoring/)

### Deployment
| Component | Instance Type | Count |
|---|---|---|
| Server-v2 | t3.micro | 1 / 2 / 4 |
| Consumer | t3.micro | 1 |
| RabbitMQ | t3.micro | 1 (Docker) |
| Redis | t3.micro | 1 (Docker) |

Region: `us-west-2`

---

## Repository Structure

```
ChatFlow_CS6650/
├── README.md
├── .gitignore
│
├── server/                  # Assignment 1 — WebSocket server
├── client-part1/            # Assignment 1 — basic load test client
├── client-part2/            # Assignment 1 — client with latency metrics
│
├── server-v2/               # Assignment 2 — distributed WebSocket server
├── consumer/                # Assignment 2 — RabbitMQ consumer service
├── client-v2/               # Assignment 2 — redesigned load test client
├── deployment/              # Assignment 2 — Docker Compose, EC2 setup, ALB
├── monitoring/              # Assignment 2 — graph generation scripts
│
├── results/                 # Assignment 1 test outputs and charts
├── results/v2/              # Assignment 2 test outputs and charts
└── doc/                     # Architecture document and diagrams
```

---

## Technologies Used

### Assignment 1
- **Java 21**, **Spring Boot**, **Spring WebSocket**
- **Jackson**, **JUnit 5**, **Mockito**, **JaCoCo**
- **AWS EC2**

### Assignment 2
- **RabbitMQ 3** (AMQP, topic exchange)
- **Redis 7** (Pub/Sub)
- **Docker**, **Docker Compose**
- **AWS ALB**, **AWS EC2**
- **Python 3** (matplotlib, pandas — graph generation)

---

## Author

Lelin Zheng
CS6650 – Scalable Distributed Systems
Northeastern University
