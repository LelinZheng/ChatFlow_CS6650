package edu.northeastern.cs6650.client.generator;

import static org.junit.jupiter.api.Assertions.*;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.model.MessageType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MessageGenerator.
 *
 * <p>The generator is run synchronously in a background thread and the queue is
 * drained after it completes so that the produced messages can be inspected.</p>
 */
class MessageGeneratorTest {

  private static final int ROOMS = 20;
  private static final int SEED_COUNT = 1000;

  /**
   * Runs the generator to completion and returns all non-poison messages in order,
   * followed by all poison pills counted separately.
   */
  private Result runGenerator(int messageCount, int workerCount) throws InterruptedException {
    // Queue capacity must fit seeds + main + poison pills without blocking
    BlockingQueue<ChatMessage> queue = new ArrayBlockingQueue<>(messageCount + workerCount + 100);
    MessageGenerator gen = new MessageGenerator(queue, ROOMS, messageCount, workerCount);

    Thread t = new Thread(gen);
    t.start();
    t.join(30_000); // generous timeout
    assertFalse(t.isAlive(), "Generator thread did not finish in time");

    List<ChatMessage> messages = new ArrayList<>();
    int poisonCount = 0;
    ChatMessage item;
    while ((item = queue.poll()) != null) {
      if (item.isPoison()) {
        poisonCount++;
      } else {
        messages.add(item);
      }
    }
    return new Result(messages, poisonCount);
  }

  @Test
  void totalMessageCount_equalsMessageCount() throws InterruptedException {
    int messageCount = 1200;
    Result r = runGenerator(messageCount, 4);
    assertEquals(messageCount, r.messages.size(),
        "Queue should contain exactly messageCount non-poison messages");
  }

  @Test
  void poisonPillCount_equalsWorkerCount() throws InterruptedException {
    int workerCount = 8;
    Result r = runGenerator(1100, workerCount);
    assertEquals(workerCount, r.poisonCount,
        "Should enqueue exactly one poison pill per worker");
  }

  @Test
  void seedPhase_firstMessagesAreAllJoins() throws InterruptedException {
    Result r = runGenerator(1100, 1);
    for (int i = 0; i < SEED_COUNT; i++) {
      assertEquals(MessageType.JOIN, r.messages.get(i).getMessageType(),
          "Seed message at index " + i + " should be JOIN");
    }
  }

  @Test
  void seedPhase_noDuplicateUserRoomPairs() throws InterruptedException {
    Result r = runGenerator(1100, 1);
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < SEED_COUNT; i++) {
      ChatMessage msg = r.messages.get(i);
      String key = msg.getUserId() + ":" + msg.getRoomId();
      assertTrue(seen.add(key),
          "Duplicate user+room pair in seed phase: " + key);
    }
  }

  @Test
  void allMessages_haveRequiredFields() throws InterruptedException {
    Result r = runGenerator(1100, 1);
    for (ChatMessage msg : r.messages) {
      assertNotNull(msg.getMessageId(), "messageId must not be null");
      assertNotNull(msg.getUserId(),    "userId must not be null");
      assertNotNull(msg.getUsername(),  "username must not be null");
      assertNotNull(msg.getMessage(),   "message content must not be null");
      assertNotNull(msg.getMessageType(), "messageType must not be null");
      assertNotNull(msg.getRoomId(),    "roomId must not be null");
      assertNotNull(msg.getTimestamp(), "timestamp must not be null");
    }
  }

  @Test
  void allMessages_haveRoomIdInValidRange() throws InterruptedException {
    Result r = runGenerator(1100, 1);
    for (ChatMessage msg : r.messages) {
      int roomId = Integer.parseInt(msg.getRoomId());
      assertTrue(roomId >= 1 && roomId <= ROOMS,
          "roomId " + roomId + " out of range [1, " + ROOMS + "]");
    }
  }

  @Test
  void textAndLeave_alwaysHaveValidMembership() throws InterruptedException {
    // Replay the message stream with our own membership tracking and verify
    // every TEXT and LEAVE appears only when the user is in the room.
    Result r = runGenerator(1500, 1);
    Map<String, Set<String>> membership = new HashMap<>();

    for (ChatMessage msg : r.messages) {
      String userId = msg.getUserId();
      String roomId = msg.getRoomId();
      MessageType type = msg.getMessageType();

      if (type == MessageType.JOIN) {
        membership.computeIfAbsent(userId, k -> new HashSet<>()).add(roomId);
      } else if (type == MessageType.TEXT) {
        Set<String> rooms = membership.get(userId);
        assertTrue(rooms != null && rooms.contains(roomId),
            "TEXT message for user=" + userId + " room=" + roomId
                + " but user is not in that room");
      } else if (type == MessageType.LEAVE) {
        Set<String> rooms = membership.get(userId);
        assertTrue(rooms != null && rooms.contains(roomId),
            "LEAVE message for user=" + userId + " room=" + roomId
                + " but user is not in that room");
        rooms.remove(roomId);
      }
    }
  }

  @Test
  void leave_updatesGeneratorMap_soSubsequentLeaveIsNotGenerated()
      throws InterruptedException {
    // After a LEAVE is generated for user+room, no further LEAVE should appear
    // for that exact same pair (unless a new JOIN precedes it).
    Result r = runGenerator(2000, 1);
    Map<String, Set<String>> membership = new HashMap<>();

    for (ChatMessage msg : r.messages) {
      String userId = msg.getUserId();
      String roomId = msg.getRoomId();
      switch (msg.getMessageType()) {
        case JOIN:
          membership.computeIfAbsent(userId, k -> new HashSet<>()).add(roomId);
          break;
        case LEAVE:
          Set<String> rooms = membership.get(userId);
          assertNotNull(rooms, "LEAVE without prior JOIN for user=" + userId);
          assertTrue(rooms.contains(roomId),
              "Double LEAVE for user=" + userId + " room=" + roomId);
          rooms.remove(roomId);
          break;
        default:
          break;
      }
    }
  }

  @Test
  void mainPhase_usernameMatchesUserId() throws InterruptedException {
    Result r = runGenerator(1100, 1);
    // In the main phase (after seed), TEXT and LEAVE have userId overridden by the
    // generator, and username must be "user" + userId.
    for (int i = SEED_COUNT; i < r.messages.size(); i++) {
      ChatMessage msg = r.messages.get(i);
      assertEquals("user" + msg.getUserId(), msg.getUsername(),
          "username should be 'user' + userId for message at index " + i);
    }
  }

  // ---- helper ----

  private static class Result {
    final List<ChatMessage> messages;
    final int poisonCount;

    Result(List<ChatMessage> messages, int poisonCount) {
      this.messages = messages;
      this.poisonCount = poisonCount;
    }
  }
}
