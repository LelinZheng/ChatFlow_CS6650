-- ChatFlow CS6650 Assignment 3 - Database Schema
-- PostgreSQL 16
-- Run once to initialize the database (local Docker or AWS RDS)
--
-- Usage:
--   Local:  psql -h localhost -U chatflow -d chatflow -f database/schema.sql
--   RDS:    psql -h <rds-endpoint> -U chatflow -d chatflow -f database/schema.sql

-- ============================================================
-- MESSAGES TABLE
-- ============================================================
-- One row per message consumed from RabbitMQ.
-- message_id is the client-assigned UUID used for idempotent upserts.
-- created_at is parsed from the ISO-8601 timestamp string on the message.

CREATE TABLE IF NOT EXISTS messages (
    message_id   VARCHAR(36)   PRIMARY KEY,
    room_id      VARCHAR(50)   NOT NULL,
    user_id      VARCHAR(50)   NOT NULL,
    username     VARCHAR(100)  NOT NULL,
    content      TEXT          NOT NULL,
    message_type VARCHAR(10)   NOT NULL,    -- TEXT, JOIN, LEAVE
    server_id    VARCHAR(100),
    client_ip    VARCHAR(50),
    created_at   TIMESTAMPTZ   NOT NULL
);

-- ============================================================
-- INDEXES
-- ============================================================

-- Core query 1: messages in a room ordered by time
CREATE INDEX IF NOT EXISTS idx_messages_room_time
    ON messages (room_id, created_at);

-- Core query 2: user message history with optional date range
CREATE INDEX IF NOT EXISTS idx_messages_user_time
    ON messages (user_id, created_at);

-- Core query 4: rooms a user participated in + last activity
CREATE INDEX IF NOT EXISTS idx_messages_user_room
    ON messages (user_id, room_id, created_at DESC);

-- Core query 3 + analytics: time-window aggregations (active users, msg/sec stats)
CREATE INDEX IF NOT EXISTS idx_messages_created_at
    ON messages (created_at);
