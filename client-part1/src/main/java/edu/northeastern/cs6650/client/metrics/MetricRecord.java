package edu.northeastern.cs6650.client.metrics;

import edu.northeastern.cs6650.client.model.MessageType;

/**
 * Represents a single performance metric record from a WebSocket message exchange.
 *
 * <p>Each record captures the timestamp, message type, round-trip latency, status,
 * and room ID for a message sent during load testing. Records are collected in
 * real-time and written to CSV for post-test analysis.</p>
 *
 * <p>This class supports a poison-pill pattern for signaling completion to
 * consumers processing metric records from a queue.</p>
 */
public class MetricRecord {
  private final long timestampMillis;
  private final MessageType messageType;
  private final long latencyMillis;
  private final String statusCode;
  private final int roomId;

  // control flag (not written to CSV)
  private boolean poison;

  /**
   * Constructs a MetricRecord with the specified performance data.
   *
   * @param timestampMillis the Unix epoch timestamp (in milliseconds) when the message was sent
   * @param messageType the type of message (JOIN, TEXT, or LEAVE)
   * @param latencyMillis the round-trip latency in milliseconds, or -1 if the message failed
   * @param statusCode the outcome status ("OK", "FAILED_AFTER_RETRIES", "NO_CONNECTION", etc.)
   * @param roomId the chat room ID where the message was sent
   */
  public MetricRecord(long timestampMillis, MessageType messageType, long latencyMillis,
      String statusCode, int roomId) {
    this.timestampMillis = timestampMillis;
    this.messageType = messageType;
    this.latencyMillis = latencyMillis;
    this.statusCode = statusCode;
    this.roomId = roomId;
    this.poison = false;
  }

  /**
   * Returns the timestamp when the message was sent.
   *
   * @return Unix epoch timestamp in milliseconds
   */
  public long getTimestampMillis() {
    return timestampMillis;
  }

  /**
   * Returns the type of message.
   *
   * @return the message type (JOIN, TEXT, or LEAVE)
   */
  public MessageType getMessageType() {
    return messageType;
  }

  /**
   * Returns the measured round-trip latency.
   *
   * @return latency in milliseconds, or -1 if the message failed
   */
  public long getLatencyMillis() {
    return latencyMillis;
  }

  /**
   * Returns the status code indicating the message outcome.
   *
   * @return status code string ("OK", "FAILED_AFTER_RETRIES", "NO_CONNECTION", etc.)
   */
  public String getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the room ID where the message was sent.
   *
   * @return the chat room ID
   */
  public int getRoomId() {
    return roomId;
  }

  /**
   * Checks whether this record is a poison-pill sentinel.
   *
   * <p>Poison-pill records are used to signal consumers that no more records
   * will be produced and they should terminate gracefully.</p>
   *
   * @return {@code true} if this is a poison-pill record; {@code false} otherwise
   */
  public boolean isPoison() {
    return poison;
  }

  /**
   * Creates a poison-pill record to signal completion.
   *
   * <p>This factory method creates a special MetricRecord that signals to
   * consumers (such as {@link CsvMetricsWriter}) that metric collection has
   * completed and they should terminate.</p>
   *
   * @return a poison-pill MetricRecord
   */
  public static MetricRecord poison() {
    MetricRecord m = new MetricRecord(
        0, null, 0, null, 0);
    m.poison = true;
    return m;
  }
}
