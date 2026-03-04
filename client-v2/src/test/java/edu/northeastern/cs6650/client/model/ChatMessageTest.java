package edu.northeastern.cs6650.client.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ChatMessage class.
 */
class ChatMessageTest {

  @Test
  void testDefaultConstructor() {
    ChatMessage message = new ChatMessage();
    assertNotNull(message);
    assertNull(message.getUserId());
    assertNull(message.getUsername());
    assertNull(message.getMessage());
    assertNull(message.getMessageType());
    assertNull(message.getTimestamp());
    assertNull(message.getRoomId());
  }

  @Test
  void testParameterizedConstructor() {
    ChatMessage message = new ChatMessage(
        "12345",
        "user12345",
        "Hello World",
        MessageType.TEXT,
        "2024-01-01T12:00:00Z",
        "5"
    );

    assertEquals("12345", message.getUserId());
    assertEquals("user12345", message.getUsername());
    assertEquals("Hello World", message.getMessage());
    assertEquals(MessageType.TEXT, message.getMessageType());
    assertEquals("2024-01-01T12:00:00Z", message.getTimestamp());
    assertEquals("5", message.getRoomId());
  }

  @Test
  void testSettersAndGetters() {
    ChatMessage message = new ChatMessage();

    message.setUserId("67890");
    message.setUsername("user67890");
    message.setMessage("Test message");
    message.setMessageType(MessageType.JOIN);
    message.setTimestamp("2024-01-02T10:30:00Z");
    message.setMessageId("msg-123");
    message.setRoomId("10");

    assertEquals("67890", message.getUserId());
    assertEquals("user67890", message.getUsername());
    assertEquals("Test message", message.getMessage());
    assertEquals(MessageType.JOIN, message.getMessageType());
    assertEquals("2024-01-02T10:30:00Z", message.getTimestamp());
    assertEquals("msg-123", message.getMessageId());
    assertEquals("10", message.getRoomId());
  }

  @Test
  void testIsPoisonReturnsFalseForNormalMessage() {
    ChatMessage message = new ChatMessage();
    message.setMessageId("normal-message-id");
    assertFalse(message.isPoison());
  }

  @Test
  void testIsPoisonReturnsFalseForNullMessageId() {
    ChatMessage message = new ChatMessage();
    assertFalse(message.isPoison());
  }

  @Test
  void testPoisonFactoryMethod() {
    ChatMessage poison = ChatMessage.poison();

    assertNotNull(poison);
    assertTrue(poison.isPoison());
    assertEquals("POISON", poison.getMessageId());
  }

  @Test
  void testPoisonPillDetection() {
    ChatMessage poison = ChatMessage.poison();
    ChatMessage normal = new ChatMessage();
    normal.setMessageId("regular-id");

    assertTrue(poison.isPoison());
    assertFalse(normal.isPoison());
  }

  @Test
  void testAllMessageTypes() {
    ChatMessage textMsg = new ChatMessage();
    textMsg.setMessageType(MessageType.TEXT);
    assertEquals(MessageType.TEXT, textMsg.getMessageType());

    ChatMessage joinMsg = new ChatMessage();
    joinMsg.setMessageType(MessageType.JOIN);
    assertEquals(MessageType.JOIN, joinMsg.getMessageType());

    ChatMessage leaveMsg = new ChatMessage();
    leaveMsg.setMessageType(MessageType.LEAVE);
    assertEquals(MessageType.LEAVE, leaveMsg.getMessageType());
  }
}