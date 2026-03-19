package edu.northeastern.cs6650.consumer.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Thread-safe counters tracking Stage 1 consumer pipeline performance.
 *
 * <p>Stage 1: consumer threads read from RabbitMQ, fan out via Redis, and enqueue
 * to the BatchWriter buffer. These metrics are independent of DB write performance.
 *
 * <p>All counters are cumulative since application startup.
 * Exposed via HealthController alongside {@link BatchMetrics} for full pipeline visibility.
 */
@Component
public class ConsumerMetrics {

  private final AtomicLong messagesReceived = new AtomicLong();
  private final AtomicLong messagesPublished = new AtomicLong();
  private final AtomicLong nackCount = new AtomicLong();
  private final AtomicLong duplicateCount = new AtomicLong();
  private final AtomicLong totalProcessingLatencyMs = new AtomicLong();
  private final AtomicLong processedCount = new AtomicLong();
  private volatile int activeThreadCount;
  private final long startTimeMs = System.currentTimeMillis();

  /** Increments the count of messages received from RabbitMQ. */
  public void incrementReceived() { messagesReceived.incrementAndGet(); }

  /** Increments the count of messages successfully published to Redis. */
  public void incrementPublished() { messagesPublished.incrementAndGet(); }

  /** Increments the count of messages nacked after all retry attempts exhausted. */
  public void incrementNacked() { nackCount.incrementAndGet(); }

  /** Increments the count of duplicate messages skipped via dedup cache. */
  public void incrementDuplicate() { duplicateCount.incrementAndGet(); }

  /**
   * Records the processing latency for one successfully delivered message.
   * Latency is measured from RabbitMQ delivery to Redis ack.
   *
   * @param latencyMs time from receive to ack in milliseconds
   */
  public void recordProcessing(long latencyMs) {
    processedCount.incrementAndGet();
    totalProcessingLatencyMs.addAndGet(latencyMs);
  }

  /**
   * Sets the number of active consumer threads in the pool.
   * Called once during ConsumerThreadPool initialization.
   *
   * @param count number of consumer threads
   */
  public void setActiveThreadCount(int count) { this.activeThreadCount = count; }

  public long getMessagesReceived() { return messagesReceived.get(); }
  public long getMessagesPublished() { return messagesPublished.get(); }
  public long getNackCount() { return nackCount.get(); }
  public long getDuplicateCount() { return duplicateCount.get(); }
  public int getActiveThreadCount() { return activeThreadCount; }

  /** Returns application uptime in seconds (minimum 1 to avoid division by zero). */
  public long getUptimeSeconds() {
    return Math.max(1, (System.currentTimeMillis() - startTimeMs) / 1000);
  }

  /** Returns average messages received per second since startup. */
  public long getReceivedPerSec() {
    return messagesReceived.get() / getUptimeSeconds();
  }

  /** Returns average messages published to Redis per second since startup. */
  public long getPublishedPerSec() {
    return messagesPublished.get() / getUptimeSeconds();
  }

  /**
   * Returns average processing latency in milliseconds (receive → ack).
   * Returns 0 if no messages have been processed yet.
   */
  public long getAvgProcessingLatencyMs() {
    long count = processedCount.get();
    return count == 0 ? 0 : totalProcessingLatencyMs.get() / count;
  }
}
