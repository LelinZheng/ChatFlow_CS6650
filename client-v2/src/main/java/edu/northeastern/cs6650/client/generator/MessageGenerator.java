package edu.northeastern.cs6650.client.generator;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.model.MessageType;
import edu.northeastern.cs6650.client.util.MessageFactory;
import edu.northeastern.cs6650.client.util.RandomGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Generates chat messages for the load test and places them on a shared queue.
 *
 * <p>Generation runs in a single dedicated thread and proceeds in two phases,
 * both backed by a <em>generator-side membership map</em> ({@code HashMap},
 * single-threaded) that tracks which rooms each user has currently joined.</p>
 *
 * <p><strong>Seed phase ({@value #SEED_JOIN_COUNT} messages):</strong><br>
 * Generates JOIN messages for random users and rooms. Any candidate where the user
 * is already in that room is discarded and redrawn. Continues until exactly
 * {@value #SEED_JOIN_COUNT} unique new memberships are established, priming
 * the membership map so that TEXT and LEAVE messages always have a valid
 * population to sample from.</p>
 *
 * <p><strong>Main phase ({@code messageCount} − {@value #SEED_JOIN_COUNT} messages):</strong><br>
 * Generates messages with a weighted type distribution (90% TEXT, 5% JOIN, 5% LEAVE).
 * TEXT and LEAVE are produced without any retry loop: a valid (userId, roomId) pair is
 * sampled <em>directly</em> from the membership map via {@link #pickMemberPair()},
 * guaranteeing every enqueued TEXT and LEAVE is valid at generation time.
 * JOIN candidates use random userId + roomId and are discarded only if that exact
 * user+room pair already exists (rare at 5% of traffic).</p>
 *
 * <p>After all messages are enqueued, one poison-pill per worker is appended to
 * trigger graceful shutdown.</p>
 */
public class MessageGenerator implements Runnable {

  /**
   * Number of seed JOIN messages sent before the main phase to prime the membership map.
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
   * Accessed only by this generator thread; no synchronization required.
   */
  private final Map<String, Set<String>> generatorMembership = new HashMap<>();

  /**
   * Flat list of unique userIds that have joined at least one room, used for
   * O(1) random-index access when sampling a userId for TEXT and LEAVE messages.
   * Users who later leave all their rooms remain in this list; {@link #pickMemberPair()}
   * retries (very rarely) if the picked user's room set is now empty.
   */
  private final List<String> seenUsersList = new ArrayList<>();

  /**
   * Guards against duplicate entries in {@link #seenUsersList}.
   */
  private final Set<String> seenUsersSet = new HashSet<>();

  /**
   * Constructs a MessageGenerator.
   *
   * @param queue        the shared queue all workers pull from
   * @param rooms        number of chat rooms (room IDs 1 through {@code rooms})
   * @param messageCount total number of valid messages to enqueue (includes seed JOINs)
   * @param workerCount  number of worker threads; one poison pill appended per worker
   */
  public MessageGenerator(BlockingQueue<ChatMessage> queue,
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
   * Generates exactly {@value #SEED_JOIN_COUNT} JOIN messages for unique (userId, roomId)
   * pairs. Candidates where the user is already in the selected room are discarded.
   */
  private void runSeedPhase() throws InterruptedException {
    int placed = 0;
    while (placed < SEED_JOIN_COUNT) {
      ChatMessage msg = factory.createMessage(MessageType.JOIN);
      String roomId = randomRoomId();
      msg.setRoomId(roomId);

      String userId = msg.getUserId();
      if (!isMember(userId, roomId)) {
        joinRoom(userId, roomId);
        queue.put(msg);
        placed++;
      }
    }
  }

  /**
   * Generates the remaining {@code messageCount} − {@value #SEED_JOIN_COUNT} messages.
   *
   * <p>For TEXT and LEAVE, a valid (userId, roomId) pair is drawn directly from the
   * membership map — no discard-and-retry loop. For JOIN, a random userId + roomId is
   * drawn and discarded only if the user is already in that room.</p>
   */
  private void runMainPhase() throws InterruptedException {
    int target = messageCount - SEED_JOIN_COUNT;
    int placed = 0;

    while (placed < target) {
      ChatMessage msg = factory.createMessage(); // weighted random type from MessageFactory
      MessageType type = msg.getMessageType();

      if (type == MessageType.TEXT) {
        // Sample a guaranteed-valid (userId, roomId) pair directly from the map
        String[] pair = pickMemberPair();
        msg.setUserId(pair[0]);
        msg.setUsername("user" + pair[0]);
        msg.setRoomId(pair[1]);
        queue.put(msg);
        placed++;

      } else if (type == MessageType.LEAVE) {
        // Sample a guaranteed-valid pair, then remove the user from that room
        String[] pair = pickMemberPair();
        msg.setUserId(pair[0]);
        msg.setUsername("user" + pair[0]);
        msg.setRoomId(pair[1]);
        leaveRoom(pair[0], pair[1]);
        queue.put(msg);
        placed++;

      } else { // JOIN
        String roomId = randomRoomId();
        msg.setRoomId(roomId);
        String userId = msg.getUserId();
        if (!isMember(userId, roomId)) {
          joinRoom(userId, roomId);
          queue.put(msg);
          placed++;
        }
        // else: user already in this room — discard, loop continues
      }
    }
  }

  /**
   * Appends one poison-pill per worker to the queue to trigger graceful shutdown.
   */
  private void sendPoisonPills() throws InterruptedException {
    for (int i = 0; i < workerCount; i++) {
      queue.put(ChatMessage.poison());
    }
  }

  // ---- direct membership sampling ----

  /**
   * Returns a valid (userId, roomId) pair sampled directly from the current membership map.
   *
   * <p>Picks a random entry from {@link #seenUsersList} (O(1) index access) and a
   * random room from that user's room set (at most {@value #SEED_JOIN_COUNT} users ×
   * 20 rooms). If the picked user has since left all their rooms the method retries,
   * but this is extremely rare in practice because LEAVE rate ≈ JOIN rate and
   * the map is populated by the seed phase.</p>
   *
   * @return {@code [userId, roomId]}
   */
  private String[] pickMemberPair() {
    while (true) {
      int idx = randomGenerator.generateRandomInteger(0, seenUsersList.size() - 1);
      String userId = seenUsersList.get(idx);
      Set<String> userRooms = generatorMembership.get(userId);
      if (userRooms == null || userRooms.isEmpty()) {
        continue; // user left all rooms — retry (rare)
      }
      // Convert small set (≤ 20 rooms) to array for O(1) random index
      String[] roomArr = userRooms.toArray(new String[0]);
      String roomId = roomArr[randomGenerator.generateRandomInteger(0, roomArr.length - 1)];
      return new String[]{userId, roomId};
    }
  }

  // ---- generator-side membership helpers ----

  private boolean isMember(String userId, String roomId) {
    Set<String> joined = generatorMembership.get(userId);
    return joined != null && joined.contains(roomId);
  }

  private void joinRoom(String userId, String roomId) {
    generatorMembership.computeIfAbsent(userId, k -> new HashSet<>()).add(roomId);
    // Register user in the sampling list exactly once
    if (seenUsersSet.add(userId)) {
      seenUsersList.add(userId);
    }
  }

  private void leaveRoom(String userId, String roomId) {
    Set<String> joined = generatorMembership.get(userId);
    if (joined != null) {
      joined.remove(roomId);
    }
  }

  private String randomRoomId() {
    return String.valueOf(randomGenerator.generateRandomInteger(1, rooms));
  }
}
