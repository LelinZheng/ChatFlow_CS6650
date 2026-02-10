package edu.northeastern.cs6650.client.util;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.model.MessageType;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MessageFactory class.
 */
class MessageFactoryTest {

  @Test
  void testCreateMessageNotNull() {
    MessageFactory factory = new MessageFactory();
    ChatMessage message = factory.createMessage();

    assertNotNull(message);
    assertNotNull(message.getMessageId());
    assertNotNull(message.getUserId());
    assertNotNull(message.getUsername());
    assertNotNull(message.getMessage());
    assertNotNull(message.getMessageType());
    assertNotNull(message.getTimestamp());
  }

  @Test
  void testUserIdInValidRange() {
    MessageFactory factory = new MessageFactory();

    for (int i = 0; i < 100; i++) {
      ChatMessage message = factory.createMessage();
      int userId = Integer.parseInt(message.getUserId());

      assertTrue(userId >= 1 && userId <= 100000,
          "UserId " + userId + " is out of valid range [1, 100000]");
    }
  }

  @Test
  void testUsernameFormat() {
    MessageFactory factory = new MessageFactory();

    for (int i = 0; i < 50; i++) {
      ChatMessage message = factory.createMessage();
      String username = message.getUsername();

      assertTrue(username.startsWith("user"),
          "Username should start with 'user'");
      assertTrue(username.length() >= 5 && username.length() <= 24,
          "Username length out of expected range");
      assertEquals("user" + message.getUserId(), username,
          "Username should be 'user' + userId");
    }
  }

  @Test
  void testMessageIsFromPredefinedList() {
    MessageFactory factory = new MessageFactory();

    for (int i = 0; i < 100; i++) {
      ChatMessage message = factory.createMessage();
      String text = message.getMessage();

      assertNotNull(text);
      assertFalse(text.isEmpty());
      assertTrue(text.length() >= 1 && text.length() <= 500);
    }
  }

  @Test
  void testMessageTypeDistribution() {
    MessageFactory factory = new MessageFactory();
    int textCount = 0;
    int joinCount = 0;
    int leaveCount = 0;
    int totalMessages = 1000;

    for (int i = 0; i < totalMessages; i++) {
      ChatMessage message = factory.createMessage();
      switch (message.getMessageType()) {
        case TEXT:
          textCount++;
          break;
        case JOIN:
          joinCount++;
          break;
        case LEAVE:
          leaveCount++;
          break;
      }
    }

    // Approximately 90% TEXT, 5% JOIN, 5% LEAVE
    // With 1000 samples, allow some statistical variance
    assertTrue(textCount >= 850 && textCount <= 950,
        "TEXT count " + textCount + " outside expected range [850, 950]");
    assertTrue(joinCount >= 20 && joinCount <= 100,
        "JOIN count " + joinCount + " outside expected range [20, 100]");
    assertTrue(leaveCount >= 20 && leaveCount <= 100,
        "LEAVE count " + leaveCount + " outside expected range [20, 100]");

    assertEquals(totalMessages, textCount + joinCount + leaveCount,
        "Total message count mismatch");
  }

  @Test
  void testTimestampFormat() {
    MessageFactory factory = new MessageFactory();
    ChatMessage message = factory.createMessage();
    String timestamp = message.getTimestamp();

    assertNotNull(timestamp);
    // Verify it's a valid ISO-8601 timestamp by parsing it
    assertDoesNotThrow(() -> Instant.parse(timestamp),
        "Timestamp should be valid ISO-8601 format");
  }

  @Test
  void testTimestampIsRecent() {
    MessageFactory factory = new MessageFactory();
    long before = System.currentTimeMillis();
    ChatMessage message = factory.createMessage();
    long after = System.currentTimeMillis();

    Instant messageTime = Instant.parse(message.getTimestamp());
    long messageMillis = messageTime.toEpochMilli();

    assertTrue(messageMillis >= before && messageMillis <= after,
        "Message timestamp should be between test start and end");
  }

  @Test
  void testCreateMessageWithForcedType() {
    MessageFactory factory = new MessageFactory();

    ChatMessage textMessage = factory.createMessage(MessageType.TEXT);
    assertEquals(MessageType.TEXT, textMessage.getMessageType());

    ChatMessage joinMessage = factory.createMessage(MessageType.JOIN);
    assertEquals(MessageType.JOIN, joinMessage.getMessageType());

    ChatMessage leaveMessage = factory.createMessage(MessageType.LEAVE);
    assertEquals(MessageType.LEAVE, leaveMessage.getMessageType());
  }

  @Test
  void testMultipleMessagesHaveUniqueIds() {
    MessageFactory factory = new MessageFactory();
    ChatMessage msg1 = factory.createMessage();
    ChatMessage msg2 = factory.createMessage();

    assertNotEquals(msg1.getMessageId(), msg2.getMessageId(),
        "Message IDs should be unique");
  }

  @Test
  void testMessageVariety() {
    MessageFactory factory = new MessageFactory();
    Set<String> uniqueMessages = new HashSet<>();

    for (int i = 0; i < 100; i++) {
      ChatMessage message = factory.createMessage();
      uniqueMessages.add(message.getMessage());
    }

    // Should have variety in messages (at least 10 different ones in 100 samples)
    assertTrue(uniqueMessages.size() >= 10,
        "Should have variety in generated messages");
  }
}