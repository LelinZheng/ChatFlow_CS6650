package edu.northeastern.cs6650.client.metrics;

import edu.northeastern.cs6650.client.model.MessageType;

public class MetricRecord {
  private final long timestampMillis;
  private final MessageType messageType;
  private final long latencyMillis;
  private final String statusCode;
  private final int roomId;

  public MetricRecord(long timestampMillis, MessageType messageType, long latencyMillis,
      String statusCode, int roomId) {
    this.timestampMillis = timestampMillis;
    this.messageType = messageType;
    this.latencyMillis = latencyMillis;
    this.statusCode = statusCode;
    this.roomId = roomId;
  }

  public long getTimestampMillis() {
    return timestampMillis;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public long getLatencyMillis() {
    return latencyMillis;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public int getRoomId() {
    return roomId;
  }
}
