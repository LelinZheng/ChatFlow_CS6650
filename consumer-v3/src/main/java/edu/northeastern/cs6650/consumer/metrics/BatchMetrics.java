package edu.northeastern.cs6650.consumer.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Thread-safe counters tracking batch DB write performance.
 *
 * <p>All counters are cumulative since application startup.
 * Exposed via HealthController for monitoring during load tests.
 */
@Component
public class BatchMetrics {

  private final AtomicLong written = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong flushCount = new AtomicLong();
  private final AtomicLong totalFlushLatencyMs = new AtomicLong();

  /**
   * Increments the written counter by the given amount.
   *
   * @param count number of messages successfully written
   */
  public void addWritten(long count) { written.addAndGet(count); }

  /**
   * Increments the dropped counter by the given amount.
   *
   * @param count number of messages dropped
   */
  public void addDropped(long count) { dropped.addAndGet(count); }

  /**
   * Records one completed flush and its latency in milliseconds.
   *
   * @param latencyMs time taken for the flush in milliseconds
   */
  public void recordFlush(long latencyMs) {
    flushCount.incrementAndGet();
    totalFlushLatencyMs.addAndGet(latencyMs);
  }

  /**
   * Returns total messages successfully written to DB.
   *
   * @return written count
   */
  public long getWritten() { return written.get(); }

  /**
   * Returns total messages dropped due to buffer overflow or exhausted retries.
   *
   * @return dropped count
   */
  public long getDropped() { return dropped.get(); }

  /**
   * Returns total number of flushes completed.
   *
   * @return flush count
   */
  public long getFlushCount() { return flushCount.get(); }

  /**
   * Returns average flush latency in milliseconds.
   * Returns 0 if no flushes have occurred yet.
   *
   * @return average flush latency in ms
   */
  public long getAvgFlushLatencyMs() {
    long count = flushCount.get();
    return count == 0 ? 0 : totalFlushLatencyMs.get() / count;
  }
}
