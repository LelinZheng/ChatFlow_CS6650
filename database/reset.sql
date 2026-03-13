-- Reset script: run between load tests to clear all message data.
-- Table structure, indexes, and constraints are preserved.
--
-- Usage:
--   Local:  psql -h localhost -U chatflow -d chatflow -f database/reset.sql
--   RDS:    psql -h <rds-endpoint> -U chatflow -d chatflow -f database/reset.sql

TRUNCATE messages;
