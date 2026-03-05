# Deployment

This directory contains infrastructure configuration for **CS6650 Assignment 2**.
It provides the Docker Compose setup for running RabbitMQ and Redis locally or on a single EC2 instance, along with notes on the AWS deployment configuration.

---

## Contents

```
deployment/
├── docker-compose.yml    # RabbitMQ + Redis
└── README.md
```

---

## Local Setup

### Prerequisites
- Docker and Docker Compose installed

### Start RabbitMQ and Redis
```bash
cd deployment

# Set credentials (or export in your shell)
export RABBITMQ_USER=admin
export RABBITMQ_PASS=admin123

docker compose up -d
```

### Verify
```bash
# RabbitMQ Management UI
open http://localhost:15672
# Login: admin / admin123

# Redis
redis-cli ping
# Expected: PONG
```

### Stop
```bash
docker compose down
```

---

## Services

### RabbitMQ
| Setting | Value |
|---|---|
| Image | `rabbitmq:3-management` |
| AMQP port | 5672 |
| Management UI port | 15672 |
| Default credentials | `admin` / `admin123` |
| Data persistence | Docker volume `rabbitmq_data` |
| Health check | `rabbitmq-diagnostics ping` every 10s |
| Restart policy | `unless-stopped` |

### Redis
| Setting | Value |
|---|---|
| Image | `redis:7-alpine` |
| Port | 6379 |
| Restart policy | `unless-stopped` |

---

## AWS Deployment Architecture

```
                    ┌─────────────────────────┐
                    │  Application Load Balancer│
                    │  port 80 · HTTP + WS     │
                    └────────────┬────────────┘
                                 │
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                 ▼
        ┌──────────┐      ┌──────────┐      ┌──────────┐
        │Server-v2 │      │Server-v2 │  … │Server-v2 │
        │t3.micro  │      │t3.micro  │      │t3.micro  │
        │port 8080 │      │port 8080 │      │port 8080 │
        └────┬─────┘      └────┬─────┘      └────┬─────┘
             │                 │                 │
             └─────────────────┼─────────────────┘
                               │ publish / subscribe
               ┌───────────────┼────────────────┐
               ▼                                ▼
        ┌──────────┐                    ┌──────────┐
        │ RabbitMQ │                    │  Redis   │
        │t3.micro  │──── consumer ────▶│t3.micro  │
        │(Docker)  │                    │(Docker)  │
        └──────────┘                    └──────────┘
               ▲
        ┌──────────┐
        │ Consumer │
        │t3.micro  │
        └──────────┘
```

---

## EC2 Instance Summary

| Component | Instance Type | Count | Notes |
|---|---|---|---|
| Server-v2 | t3.micro | 1 / 2 / 4 | Behind ALB |
| Consumer | t3.micro | 1 | Fixed single instance |
| RabbitMQ + Redis | t3.micro | 1 | Docker Compose |

---

## ALB Configuration

| Setting | Value |
|---|---|
| Type | Application Load Balancer |
| Listener | HTTP port 80 |
| Protocol | HTTP (WebSocket upgrade supported) |
| Target group protocol | HTTP |
| Health check path | `GET /health` |
| Session stickiness | Disabled |

### Adding / Removing Server Instances
1. Launch a new `t3.micro` EC2 instance
2. Deploy server-v2 JAR and start the `systemd` service
3. Register the instance in the ALB target group
4. ALB health check confirms the instance is healthy before routing traffic

---

## Deploying server-v2 to EC2

```bash
# Build locally
cd server-v2
mvn clean package -DskipTests

# Copy JAR to EC2
scp -i ../Chat_Key.pem target/chat-server-v2.jar \
    ec2-user@<EC2_HOST>:~/

# SSH in and set up systemd service
ssh -i ../Chat_Key.pem ec2-user@<EC2_HOST>

sudo nano /etc/systemd/system/server-v2.service
```

Example `server-v2.service`:
```ini
[Unit]
Description=ChatFlow Server v2
After=network.target

[Service]
ExecStart=/usr/bin/java -jar /home/ec2-user/chat-server-v2.jar \
  --rabbitmq.host=<RABBITMQ_HOST> \
  --spring.data.redis.host=<REDIS_HOST>
Restart=always
User=ec2-user

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable server-v2
sudo systemctl start server-v2
sudo systemctl status server-v2
```

---

## Deploying consumer to EC2

```bash
# Build locally
cd consumer
mvn clean package -DskipTests

# Copy JAR to EC2
scp -i ../Chat_Key.pem target/consumer.jar \
    ec2-user@<EC2_HOST>:~/
```

Example `consumer.service` (change `consumer.thread.count` to tune):
```ini
[Unit]
Description=ChatFlow Consumer
After=network.target

[Service]
ExecStart=/usr/bin/java -jar /home/ec2-user/consumer.jar \
  --rabbitmq.host=<RABBITMQ_HOST> \
  --spring.data.redis.host=<REDIS_HOST> \
  --consumer.thread.count=20 \
  --consumer.prefetch.count=10
Restart=always
User=ec2-user

[Install]
WantedBy=multi-user.target
```

To change thread count without redeploying:
```bash
sudo vim /etc/systemd/system/consumer.service
sudo systemctl daemon-reload
sudo systemctl restart consumer
```

---

## Related Documentation

- [Main Project README](../README.md)
- [Server v2](../server-v2/README.md)
- [Consumer](../consumer/README.md)
- [Architecture Document](../doc/architecture.md)

---

## Author
Lelin Zheng
CS6650 – Scalable Distributed Systems
