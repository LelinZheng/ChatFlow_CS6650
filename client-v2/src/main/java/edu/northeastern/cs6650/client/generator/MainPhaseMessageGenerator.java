package edu.northeastern.cs6650.client.generator;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.model.MessageType;
import edu.northeastern.cs6650.client.util.MessageFactory;
import edu.northeastern.cs6650.client.util.RandomGenerator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Generates chat messages for the load test and places them on a shared queue.
 *
 * <p>Generation proceeds in two phases tracked by a <em>generator-side membership map</em>
 * ({@code HashMap}, accessed only by this single generator thread). This map reflects the
 * room membership state as seen at generation time and ensures that every TEXT and LEAVE
 * message placed on the queue is already valid before any worker picks it up.</p>
 *
 * <p><strong>Seed phase ({@value #SEED_JOIN_COUNT} messages):</strong><br>
 * Generates JOIN messages for random users and random rooms. If the selected user is
 * already a member of the selected room the candidate is discarded and a new one is
 * drawn, so exactly {@value #SEED_JOIN_COUNT} unique new memberships are established
 * before the main phase begins.</p>
 *
 * <p><strong>Main phase ({@code messageCount} - {@value #SEED_JOIN_COUNT} messages):</strong><br>
 * Generates candidates with a weighted type distribution (90% TEXT, 5% JOIN, 5% LEAVE).
 * Each candidate is validated against the generator-side map before being enqueued:</p>
 * <ul>
 *   <li>JOIN: accepted only if the user is <em>not</em> already in the room; the user
 *       is added to the map.</li>
 *   <li>TEXT: accepted only if the user is currently in the room.</li>
 *   <li>LEAVE: accepted only if the user is currently in the room; the user is removed
 *       from the map so subsequent messages respect the updated state.</li>
 * </ul>
 * <p>Discarded candidates are not counted toward {@code messageCount}; generation
 * continues until exactly {@code messageCount} valid messages have been enqueued.</p>
 *
 * <p>After all messages are enqueued, one poison-pill per worker is appended to
 * trigger graceful worker shutdown.</p>
 */
public class MainPhaseMessageGenerator implements Runnable {

  /**
   * Number of seed JOIN messages generated before the main phase.
   * Establishes an initial pool of valid user-room memberships so that
   * TEXT and LEAVE candidates in the main phase have a good hit rate.
   */
  private static final int SEED_JOIN_COUNT = 1000;

  private final BlockingQueue<ChatMessage> queue;
  private final int rooms;
  private final int messageCount;
  private final int workerCount;
  private final MessageFactory factory;
  private final RandomGenerator randomGenerator;

  /**
   * Generator-side membership map: userId → set of roomIds the user has joined.
   * Accessed exclusively by this generator thread; no synchronization required.
   */
  private final Map<String, Set<String>> generatorMembership = new HashMap<>();

  /**
   * Constructs a MainPhaseMessageGenerator.
   *
   * @param queue        the shared queue that all workers pull from
   * @param rooms        the number of chat rooms (room IDs 1 through {@code rooms})
   * @param messageCount total number of valid messages to enqueue (includes seed JOINs)
   * @param workerCount  number of worker threads; one poison pill is appended per worker
   */
  public MainPhaseMessageGenerator(BlockingQueue<ChatMessage> queue,
      int rooms,
      int messageCount,
      int workerCount) {
    this.queue = queue;
    this.rooms = rooms;
    this.messageCount = messageCount;
    this.workerCount = workerCount;
    this.factory = new MessageFactory();
    this.randomGenerator = new RandomGenerator();
  }

  /**
   * Runs the seed phase, then the main phase, then enqueues poison pills.
   */
  @Override
  public void run() {
    try {
      runSeedPhase();
      runMainPhase();
      sendPoisonPills();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Generates exactly {@value #SEED_JOIN_COUNT} unique JOIN messages.
   *
   * <p>A candidate is unique if the selected user has not already joined the selected
   * room. Duplicate candidates (user already in room) are silently discarded and a
   * new candidate is drawn until the target count is reached.</p>
   */
  private void runSeedPhase() throws InterruptedException {
    int placed = 0;
    while (placed < SEED_JOIN_COUNT) {
      ChatMessage msg = factory.createMessage(MessageType.JOIN);
      String roomId = String.valueOf(randomGenerator.generateRandomInteger(1, rooms));
      msg.setRoomId(roomId);

      String userId = msg.getUserId();
      if (!isMember(userId, roomId)) {
        joinRoom(userId, roomId);
        queue.put(msg);
        placed++;
      }
      // else: user already in this room — discard and try again
    }
  }

  /**
   * Generates {@code messageCount - SEED_JOIN_COUNT} valid messages with the
   * weighted type distribution.
   *
   * <p>Candidates that fail the membership check (TEXT/LEAVE for a user not in the room,
   * or JOIN for a user already in the room) are silently discarded and a new candidate
   * is drawn. The generator loops until exactly the required number of valid messages
   * have been placed on the queue.</p>
   */
  private void runMainPhase() throws InterruptedException {
    int target = messageCount - SEED_JOIN_COUNT;
    int placed = 0;

    while (placed < target) {
      ChatMessage candidate = factory.createMessage();
      String roomId = String.valueOf(randomGenerator.generateRandomInteger(1, rooms));
      candidate.setRoomId(roomId);

      String userId = candidate.getUserId();
      MessageType type = candidate.getMessageType();

      if (type == MessageType.JOIN) {
        if (!isMember(userId, roomId)) {
          joinRoom(userId, roomId);
          queue.put(candidate);
          placed++;
        }
        // else: user already in room — discard
      } else if (type == MessageType.TEXT) {
        if (isMember(userId, roomId)) {
          queue.put(candidate);
          placed++;
        }
        // else: user not in room — discard
      } else if (type == MessageType.LEAVE) {
        if (isMember(userId, roomId)) {
          leaveRoom(userId, roomId);
          queue.put(candidate);
          placed++;
        }
        // else: user not in room — discard
      }
    }
  }

  /**
   * Appends one poison-pill message per worker to signal graceful shutdown.
   */
  private void sendPoisonPills() throws InterruptedException {
    for (int i = 0; i < workerCount; i++) {
      queue.put(ChatMessage.poison());
    }
  }

  // ---- generator-side membership helpers (single-threaded) ----

  private boolean isMember(String userId, String roomId) {
    Set<String> joined = generatorMembership.get(userId);
    return joined != null && joined.contains(roomId);
  }

  private void joinRoom(String userId, String roomId) {
    generatorMembership.computeIfAbsent(userId, k -> new HashSet<>()).add(roomId);
  }

  private void leaveRoom(String userId, String roomId) {
    Set<String> joined = generatorMembership.get(userId);
    if (joined != null) {
      joined.remove(roomId);
    }
  }
}
