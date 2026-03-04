package edu.northeastern.cs6650.client.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RoomMembershipTracker.
 */
class RoomMembershipTrackerTest {

  private RoomMembershipTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new RoomMembershipTracker();
  }

  @Test
  void isMember_returnsFalse_whenUserHasNeverJoined() {
    assertFalse(tracker.isMember("user1", "room1"));
  }

  @Test
  void isMember_returnsTrue_afterJoin() {
    tracker.join("user1", "room1");
    assertTrue(tracker.isMember("user1", "room1"));
  }

  @Test
  void isMember_returnsFalse_forDifferentRoom() {
    tracker.join("user1", "room1");
    assertFalse(tracker.isMember("user1", "room2"));
  }

  @Test
  void isMember_returnsFalse_forDifferentUser() {
    tracker.join("user1", "room1");
    assertFalse(tracker.isMember("user2", "room1"));
  }

  @Test
  void isMember_returnsFalse_afterLeave() {
    tracker.join("user1", "room1");
    tracker.leave("user1", "room1");
    assertFalse(tracker.isMember("user1", "room1"));
  }

  @Test
  void join_isIdempotent() {
    tracker.join("user1", "room1");
    tracker.join("user1", "room1");
    assertTrue(tracker.isMember("user1", "room1"));
  }

  @Test
  void leave_doesNothing_whenUserNotInRoom() {
    assertDoesNotThrow(() -> tracker.leave("user1", "room1"));
    assertFalse(tracker.isMember("user1", "room1"));
  }

  @Test
  void leave_doesNothing_whenUserHasNoEntry() {
    assertDoesNotThrow(() -> tracker.leave("unknownUser", "room1"));
  }

  @Test
  void user_canJoinMultipleRooms() {
    tracker.join("user1", "room1");
    tracker.join("user1", "room2");
    tracker.join("user1", "room3");

    assertTrue(tracker.isMember("user1", "room1"));
    assertTrue(tracker.isMember("user1", "room2"));
    assertTrue(tracker.isMember("user1", "room3"));
  }

  @Test
  void leave_removesOnlyTargetRoom_whenUserInMultipleRooms() {
    tracker.join("user1", "room1");
    tracker.join("user1", "room2");

    tracker.leave("user1", "room1");

    assertFalse(tracker.isMember("user1", "room1"));
    assertTrue(tracker.isMember("user1", "room2"));
  }

  @Test
  void multipleUsers_trackedIndependently() {
    tracker.join("user1", "room1");
    tracker.join("user2", "room1");
    tracker.join("user3", "room2");

    assertTrue(tracker.isMember("user1", "room1"));
    assertTrue(tracker.isMember("user2", "room1"));
    assertFalse(tracker.isMember("user3", "room1"));
    assertTrue(tracker.isMember("user3", "room2"));
  }

  @Test
  void concurrentJoinsAndLeaves_doNotThrow() throws InterruptedException {
    int threads = 20;
    int opsPerThread = 100;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);
    List<Throwable> errors = new ArrayList<>();

    for (int t = 0; t < threads; t++) {
      final String userId = "user" + t;
      pool.submit(() -> {
        try {
          for (int i = 0; i < opsPerThread; i++) {
            String roomId = "room" + (i % 5);
            tracker.join(userId, roomId);
            tracker.isMember(userId, roomId);
            tracker.leave(userId, roomId);
          }
        } catch (Throwable e) {
          synchronized (errors) {
            errors.add(e);
          }
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(10, TimeUnit.SECONDS);
    pool.shutdown();
    assertTrue(errors.isEmpty(), "Concurrent operations threw exceptions: " + errors);
  }
}
