# ChatFlow – CS6650 Scalable Distributed Systems

This repository contains the implementation for **CS6650 Assignment 1**, which focuses on building a **WebSocket-based chat server** and a **multithreaded client** to simulate high-volume messaging.

The project is developed incrementally across multiple assignments. Assignment 1 establishes the foundation by implementing a WebSocket server that validates and echoes messages, along with a client capable of generating significant load.

---

## Assignment Scope (Assignment 1)

In Assignment 1, the system supports:

- WebSocket-based real-time communication
- Message validation and structured error handling
- Echoing valid messages back to the sender
- Health check REST endpoint
- Multithreaded client for load simulation (Parts 1 & 2)
- Performance measurement and analysis

Advanced features such as message distribution, persistence, and analytics will be introduced in later assignments.

---

## Repository Structure
```
chatflow_6650/
├── README.md # High-level project overview (this file)
├── .gitignore
├── server/ # WebSocket server implementation
│ ├── README.md
│ ├── pom.xml
│ └── src/
├── client-part1/ # Basic multithreaded WebSocket client
│ ├── README.md
│ └── src/
├── client-part2/ # Client with latency metrics & analysis
│ ├── README.md
│ └── src/
├── results/ # Test outputs, metrics, and charts
└── docs/ # Design document and diagrams (PDF)
```
---

## Technologies Used

- **Java 21**
- **Spring Boot & Spring WebSocket**
- **Jackson (JSON processing)**
- **JUnit 5, Mockito**
- **JaCoCo (code coverage)**
- **AWS EC2 (Free Tier, us-west-2)**

---

## Subprojects

### `/server`
Implements the WebSocket server with message validation, echo responses, and a REST health endpoint.

📖 See [`server/README.md`](server/README.md) for full details.

---

### `/client-part1`
Implements a multithreaded WebSocket client that simulates thousands of messages using persistent connections.

---

### `/client-part2`
Extends the client with detailed latency tracking, statistical analysis, and CSV output for performance evaluation.

---

## Deployment

The server is deployed on an **AWS EC2 free-tier instance** in **us-west-2**.  
Evidence of deployment and test execution is included in the submission PDF under `/results`.

---

## Author

Lelin Zheng

CS6650 – Scalable Distributed Systems  
Northeastern University  
Assignment 1 – ChatFlow
