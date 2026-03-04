package edu.northeastern.cs6650.client.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe room membership tracker shared across all {@code ConnectionWorker} threads.
 *
 * <p>Workers consult this tracker immediately before sending a message to ensure
 * the user is still a member of the target room at send time. Because multiple
 * workers process messages concurrently, a message that was valid at generation time
 * may become invalid by the time it is dequeued (e.g., a LEAVE for the same user/room
 * was processed out of order by another worker). This tracker provides a final,
 * concurrent-safe gate before any message is dispatched to the server.</p>
 *
 * <p>All operations are safe for concurrent use by multiple threads without
 * external synchronization.</p>
 */
public class RoomMembershipTracker {

  /**
   * Maps each userId to the set of roomIds the user has currently joined.
   * Inner sets use {@link ConcurrentHashMap#newKeySet()} so concurrent add/remove
   * on individual user entries is also safe.
   */
  private final ConcurrentHashMap<String, Set<String>> map = new ConcurrentHashMap<>();

  /**
   * Returns {@code true} if the user is currently a member of the given room.
   *
   * @param userId the user to check
   * @param roomId the room to check
   * @return {@code true} if the user has joined and not yet left the room
   */
  public boolean isMember(String userId, String roomId) {
    Set<String> rooms = map.get(userId);
    return rooms != null && rooms.contains(roomId);
  }

  /**
   * Records that a user has joined a room.
   *
   * <p>Safe to call even if the user is already a member; the operation is idempotent.</p>
   *
   * @param userId the user joining the room
   * @param roomId the room being joined
   */
  public void join(String userId, String roomId) {
    map.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(roomId);
  }

  /**
   * Records that a user has left a room.
   *
   * <p>Safe to call even if the user is not currently a member; the operation is idempotent.</p>
   *
   * @param userId the user leaving the room
   * @param roomId the room being left
   */
  public void leave(String userId, String roomId) {
    Set<String> rooms = map.get(userId);
    if (rooms != null) {
      rooms.remove(roomId);
    }
  }
}
