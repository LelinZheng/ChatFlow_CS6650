package edu.northeastern.cs6650.client.metrics;

import edu.northeastern.cs6650.client.model.MessageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MetricRecord class.
 */
class MetricRecordTest {

  @Test
  void testConstructorAndGetters() {
    long timestamp = System.currentTimeMillis();
    MetricRecord record = new MetricRecord(
        timestamp,
        MessageType.TEXT,
        50L,
        "OK",
        5
    );

    assertEquals(timestamp, record.getTimestampMillis());
    assertEquals(MessageType.TEXT, record.getMessageType());
    assertEquals(50L, record.getLatencyMillis());
    assertEquals("OK", record.getStatusCode());
    assertEquals(5, record.getRoomId());
    assertFalse(record.isPoison());
  }

  @Test
  void testFailedMessageWithNegativeLatency() {
    MetricRecord record = new MetricRecord(
        System.currentTimeMillis(),
        MessageType.JOIN,
        -1L,
        "FAILED_AFTER_RETRIES",
        10
    );

    assertEquals(-1L, record.getLatencyMillis());
    assertEquals("FAILED_AFTER_RETRIES", record.getStatusCode());
    assertFalse(record.isPoison());
  }

  @Test
  void testNoConnectionStatus() {
    MetricRecord record = new MetricRecord(
        System.currentTimeMillis(),
        MessageType.LEAVE,
        -1L,
        "NO_CONNECTION",
        15
    );

    assertEquals("NO_CONNECTION", record.getStatusCode());
    assertEquals(-1L, record.getLatencyMillis());
    assertFalse(record.isPoison());
  }

  @Test
  void testPoisonFactoryMethod() {
    MetricRecord poison = MetricRecord.poison();

    assertNotNull(poison);
    assertTrue(poison.isPoison());
    assertEquals(0L, poison.getTimestampMillis());
    assertNull(poison.getMessageType());
    assertEquals(0L, poison.getLatencyMillis());
    assertNull(poison.getStatusCode());
    assertEquals(0, poison.getRoomId());
  }

  @Test
  void testPoisonPillDetection() {
    MetricRecord poison = MetricRecord.poison();
    MetricRecord normal = new MetricRecord(
        System.currentTimeMillis(),
        MessageType.TEXT,
        100L,
        "OK",
        1
    );

    assertTrue(poison.isPoison());
    assertFalse(normal.isPoison());
  }

  @Test
  void testAllMessageTypes() {
    long timestamp = System.currentTimeMillis();

    MetricRecord textRecord = new MetricRecord(timestamp, MessageType.TEXT, 30L, "OK", 1);
    assertEquals(MessageType.TEXT, textRecord.getMessageType());

    MetricRecord joinRecord = new MetricRecord(timestamp, MessageType.JOIN, 40L, "OK", 2);
    assertEquals(MessageType.JOIN, joinRecord.getMessageType());

    MetricRecord leaveRecord = new MetricRecord(timestamp, MessageType.LEAVE, 35L, "OK", 3);
    assertEquals(MessageType.LEAVE, leaveRecord.getMessageType());
  }

  @Test
  void testMultipleRooms() {
    for (int roomId = 1; roomId <= 20; roomId++) {
      MetricRecord record = new MetricRecord(
          System.currentTimeMillis(),
          MessageType.TEXT,
          50L,
          "OK",
          roomId
      );
      assertEquals(roomId, record.getRoomId());
    }
  }
}