# ChatFlow WebSocket Server

This module contains the **WebSocket server implementation** for **CS6650 Assignment 1**.  
The server accepts WebSocket connections, validates incoming chat messages, and echoes valid messages back to the sender with a server-generated timestamp.

This server serves as the foundation for a scalable chat system that will be expanded in later assignments.

---

## Features

- WebSocket endpoint with dynamic room parameter: `/chat/{roomId}`
- JSON message validation with detailed error responses
- Stateless, thread-safe message handling
- Health check REST endpoint
- Built using **Spring WebSocket**
- Deployed on AWS EC2 

---

## Technology Stack

- **Java 21**
- **Spring Boot**
- **Spring WebSocket**
- **Jackson**
- **JUnit 5 + Mockito**
- **JaCoCo**

---

## WebSocket API

### Endpoint
```
ws://<host>:<port>/chat/{roomId}
```
- The `roomId` path parameter is accepted to satisfy the API contract.
- **Assignment 1 does not require room-based message routing**, so messages are echoed only to the sender.
- Room-based distribution will be implemented in later assignments.

---

### Message Format

Incoming messages must be valid JSON with the following structure:

```json
{
  "userId": "string (1-100000)",
  "username": "string (3-20 chars)",
  "message": "string (1-500 chars)",
  "timestamp": "ISO-8601 timestamp",
  "messageType": "TEXT | JOIN | LEAVE"
}
```
---
### Validation Rules

Each incoming message is validated:

- userId must be numeric and between 1–100000

- username must be 3–20 alphanumeric characters (including underscore)

- message must be 1–500 characters for TEXT messages

- timestamp must be valid ISO-8601

- messageType must be present and valid
---
### Server Responses
### Successful Message (Echo)
```json
{
  "status": "OK",
  "message": "hello world",
  "serverTimestamp": "2026-01-27T03:15:42.123Z"
}
```
### Validation Error
```json
{
  "status": "ERROR",
  "errorCode": "VALIDATION_FAILED",
  "message": "Message validation failed",
  "details": [
    "username must be 3-20 alphanumeric characters",
    "timestamp must be in ISO 8601 format"
  ]
}
```
### Invalid JSON
```json
{
  "status": "ERROR",
  "errorCode": "INVALID_JSON",
  "message": "Malformed JSON payload",
  "details": [
    "Unexpected character encountered during parsing"
  ]
}
```
---
### REST API
### Health Check
#### Endpoint
``` bash
GET /health
```
#### Response
``` json
{
  "status": "UP",
  "timestamp": "2026-01-27T03:12:10.456Z"
}
```
---
### Thread Safety & Connection Management
- WebSocket handler is stateless

- No shared mutable state across connections

- Validation and message handling use local variables only

- Jackson ObjectMapper is thread-safe after initialization

- Connection lifecycle hooks implemented:

    - Connection established

    - Connection closed

This design ensures safe concurrent handling of messages.

---
## Running Locally

### Prerequisites
- Java 21
- Maven 3.9+
### Build and Run
``` bash
mvn clean package
java -jar target/chat-server.jar
```
Default port: **8080**

---
## Testing
### Unit Tests
``` bash
mvn test
```
### Code Coverage
JaCoCo report generated at:
``` bash
target/site/jacoco/index.html
```

---
## Manual Testing
### Using wscat
``` bash
wscat -c ws://localhost:8080/chat/1
```
Example message:
``` bash
{"userId":"1","username":"user1","message":"hello","timestamp":"2026-01-27T02:30:00Z","messageType":"TEXT"}
```
---

## Deployment (AWS EC2)

- Instance type: `t2.micro`
- Region: `us-west-2`
- OS: Amazon Linux
- Security Group:
  - TCP **8080** (application)
  - TCP **22** (SSH)
- Java 21 (Amazon Corretto)
- Application packaged as a standalone Spring Boot JAR
- Service managed using `systemd` for automatic restart and persistence across reboots

---
### Notes on Room Handling

The server accepts a {roomId} parameter as part of the WebSocket endpoint.
In Assignment 1, **messages are not routed or echoed based on roomId**, as message distribution is introduced in later assignments.
---
## Author
Lelin Zheng
CS6650 – Scalable Distributed Systems