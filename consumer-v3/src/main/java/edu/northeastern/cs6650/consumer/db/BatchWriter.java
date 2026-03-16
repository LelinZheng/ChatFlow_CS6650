package edu.northeastern.cs6650.consumer.db;

import edu.northeastern.cs6650.consumer.circuitbreaker.DbCircuitBreaker;
import edu.northeastern.cs6650.consumer.metrics.BatchMetrics;
import edu.northeastern.cs6650.consumer.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Buffers incoming chat messages and flushes them to PostgreSQL in batches.
 *
 * <p>Two flush triggers:
 * <ul>
 *   <li>Time-based: a scheduled flush runs every {@code db.flush.interval.ms}</li>
 *   <li>Size-based: an immediate flush is submitted when the buffer reaches
 *       {@code db.batch.size}</li>
 * </ul>
 *
 * <p>Failed flushes are retried up to {@code db.max.retries} times with
 * exponential backoff. Messages that cannot be persisted after all retries,
 * or when the circuit breaker is open, are sent to the {@link DeadLetterQueue}.
 */
@Component
public class BatchWriter {

  private static final Logger log = LoggerFactory.getLogger(BatchWriter.class);

  @Value("${db.batch.size:500}")
  private int batchSize;

  @Value("${db.flush.interval.ms:500}")
  private long flushIntervalMs;

  @Value("${db.writer.thread.count:2}")
  private int writerThreadCount;

  @Value("${db.buffer.max.size:50000}")
  private int bufferMaxSize;

  @Value("${db.max.retries:3}")
  private int maxRetries;

  private final MessageRepository repository;
  private final DbCircuitBreaker circuitBreaker;
  private final DeadLetterQueue dlq;
  private final BatchMetrics metrics;

  private LinkedBlockingQueue<ChatMessage> buffer;
  private ScheduledExecutorService scheduler;

  /**
   * Constructs a BatchWriter with all required dependencies.
   *
   * @param repository     the repository used to batch insert messages
   * @param circuitBreaker the DB circuit breaker
   * @param dlq            the dead letter queue for undeliverable messages
   * @param metrics        counters for monitoring write performance
   */
  public BatchWriter(MessageRepository repository, DbCircuitBreaker circuitBreaker,
      DeadLetterQueue dlq, BatchMetrics metrics) {
    this.repository = repository;
    this.circuitBreaker = circuitBreaker;
    this.dlq = dlq;
    this.metrics = metrics;
  }

  /**
   * Initializes the buffer and starts the scheduled flush task.
   * Called automatically by Spring after the bean is constructed and {@code @Value} fields injected.
   */
  @PostConstruct
  public void start() {
    buffer = new LinkedBlockingQueue<>(bufferMaxSize);
    scheduler = new ScheduledThreadPoolExecutor(writerThreadCount);
    scheduler.scheduleAtFixedRate(this::drainAndWrite,
        flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    log.info("BatchWriter started: batchSize={}, flushIntervalMs={}, threads={}",
        batchSize, flushIntervalMs, writerThreadCount);
  }

  /**
   * Shuts down the scheduler gracefully on application stop.
   * Called automatically by Spring before the bean is destroyed.
   */
  @PreDestroy
  public void stop() {
    scheduler.shutdown();
    log.info("BatchWriter stopped");
  }

  /**
   * Enqueues a message into the buffer for batch writing.
   * Non-blocking — if the buffer is full the message goes directly to the DLQ.
   * If the buffer reaches the batch size threshold, an immediate flush is triggered.
   *
   * @param msg the message to enqueue
   */
  public void enqueue(ChatMessage msg) {
    if (!buffer.offer(msg)) {
      dlq.add(msg);
      metrics.addDropped(1);
      log.warn("Buffer full, message dropped to DLQ: {}", msg.getMessageId());
      return;
    }
    if (buffer.size() >= batchSize) {
      scheduler.submit(this::drainAndWrite);
    }
  }

  /**
   * Returns the current number of messages waiting in the buffer.
   *
   * @return buffer size
   */
  public int bufferSize() { return buffer.size(); }

  /**
   * Drains up to {@code batchSize} messages from the buffer and writes them to PostgreSQL.
   * Skips the write if the circuit breaker is open, sending the batch to the DLQ instead.
   * Retries on failure with exponential backoff up to {@code maxRetries} attempts.
   * Package-private for testing.
   */
  void drainAndWrite() {
    List<ChatMessage> batch = new ArrayList<>(batchSize);
    buffer.drainTo(batch, batchSize);
    if (batch.isEmpty()) return;

    if (!circuitBreaker.allowRequest()) {
      log.warn("Circuit breaker open, sending {} messages to DLQ", batch.size());
      batch.forEach(dlq::add);
      metrics.addDropped(batch.size());
      return;
    }

    long start = System.currentTimeMillis();
    boolean success = false;
    long delay = 200;

    for (int attempt = 1; attempt <= maxRetries && !success; attempt++) {
      try {
        if (attempt > 1) {
          log.warn("DB write retry attempt {}/{}", attempt, maxRetries);
          Thread.sleep(Math.min(delay, 5000));
          delay *= 2;
        }
        repository.batchInsert(batch);
        success = true;
        circuitBreaker.recordSuccess();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("DB write failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
        circuitBreaker.recordFailure();
      }
    }

    if (!success) {
      log.error("All retries exhausted, sending {} messages to DLQ", batch.size());
      batch.forEach(dlq::add);
      metrics.addDropped(batch.size());
    } else {
      metrics.addWritten(batch.size());
      metrics.recordFlush(System.currentTimeMillis() - start);
    }
  }
}
