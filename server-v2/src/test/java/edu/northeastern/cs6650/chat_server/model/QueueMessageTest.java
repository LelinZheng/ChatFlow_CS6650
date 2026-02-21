package edu.northeastern.cs6650.chat_server.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class QueueMessageTest {

  private QueueMessage msg;

  @BeforeEach
  void setUp() {
    msg = new QueueMessage(
        "msg-uuid-123",
        "5",
        "42",
        "testuser",
        "hello world",
        "2026-01-01T00:00:00Z",
        "TEXT",
        "/0:0:0:0:0:0:0:1:8080",
        "/127.0.0.1:54321"
    );
  }

  @Test
  void constructor_setsAllFieldsCorrectly() {
    assertAll(
        () -> assertEquals("msg-uuid-123", msg.getMessageId()),
        () -> assertEquals("5",            msg.getRoomId()),
        () -> assertEquals("42",           msg.getUserId()),
        () -> assertEquals("testuser",     msg.getUsername()),
        () -> assertEquals("hello world",  msg.getMessage()),
        () -> assertEquals("2026-01-01T00:00:00Z", msg.getTimestamp()),
        () -> assertEquals("TEXT",         msg.getMessageType()),
        () -> assertEquals("/0:0:0:0:0:0:0:1:8080", msg.getServerId()),
        () -> assertEquals("/127.0.0.1:54321",      msg.getClientIp())
    );
  }

  @Test
  void constructor_allowsNullFields() {
    // QueueMessage is a data carrier — nulls should not throw
    assertDoesNotThrow(() -> new QueueMessage(
        null, null, null, null, null, null, null, null, null
    ));
  }

  @Test
  void jacksonCanSerializeToJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(msg);

    JsonNode root = mapper.readTree(json);
    assertEquals("msg-uuid-123", root.get("messageId").asText());
    assertEquals("5",            root.get("roomId").asText());
    assertEquals("TEXT",         root.get("messageType").asText());
  }

  @Test
  void jacksonCanDeserializeFromJson() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(msg);

    QueueMessage deserialized = mapper.readValue(json, QueueMessage.class);
    assertEquals(msg.getMessageId(), deserialized.getMessageId());
    assertEquals(msg.getRoomId(),    deserialized.getRoomId());
    assertEquals(msg.getMessage(),   deserialized.getMessage());
  }
}