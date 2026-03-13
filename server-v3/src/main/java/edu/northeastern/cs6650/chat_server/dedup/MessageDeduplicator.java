package edu.northeastern.cs6650.chat_server.dedup;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Server-side idempotency guard for incoming WebSocket messages.
 * <p>
 * Tracks recently seen message IDs in a time-bounded in-memory cache.
 * If a message ID has been seen within the TTL window, {@link #isDuplicate}
 * returns {@code true} and the caller should drop the message.
 * <p>
 * Eviction runs inline whenever the cache exceeds {@link #EVICT_THRESHOLD},
 * removing all entries older than {@link #TTL_MS}. This avoids unbounded
 * memory growth during high-throughput load tests without requiring a
 * background thread.
 */
@Component
public class MessageDeduplicator {

  private static final long TTL_MS = 60_000;
  private static final int EVICT_THRESHOLD = 10_000;

  private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

  /**
   * Returns {@code true} if {@code messageId} has already been seen within
   * the TTL window, {@code false} if it is new (and records it).
   *
   * @param messageId the client-assigned message ID to check
   * @return true if duplicate, false if first occurrence
   */
  public boolean isDuplicate(String messageId) {
    boolean duplicate = seen.putIfAbsent(messageId, System.currentTimeMillis()) != null;
    if (seen.size() > EVICT_THRESHOLD) {
      evict();
    }
    return duplicate;
  }

  private void evict() {
    long cutoff = System.currentTimeMillis() - TTL_MS;
    seen.entrySet().removeIf(e -> e.getValue() < cutoff);
  }
}
