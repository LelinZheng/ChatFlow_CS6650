package edu.northeastern.cs6650.consumer.consumer;

import edu.northeastern.cs6650.consumer.db.BatchWriter;
import edu.northeastern.cs6650.consumer.model.ChatMessage;
import edu.northeastern.cs6650.consumer.redis.RedisPublisher;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Dead letter queue for messages that failed Redis broadcast after all inline retries.
 *
 * <p>When {@link RoomConsumer} exhausts its retry budget for a message, it acks the
 * RabbitMQ delivery (taking ownership) and deposits the message here instead of nacking
 * with {@code requeue=false}. This prevents silent message loss and preserves
 * at-least-once delivery.
 *
 * <p>A background thread retries publishing each queued message to Redis on a fixed
 * interval. On success the message is forwarded to the {@link BatchWriter} for DB
 * persistence. Messages stay in the queue until they are successfully published —
 * they are never silently discarded.
 *
 * <p>If the queue reaches its capacity limit, the incoming message is logged as
 * undeliverable rather than silently dropped, making the failure observable.
 */
@Component
public class BroadcastDLQ {

  private static final Logger log = LoggerFactory.getLogger(BroadcastDLQ.class);

  @Value("${broadcast.dlq.retry.interval.ms:2000}")
  private long retryIntervalMs;

  @Value("${broadcast.dlq.max.size:10000}")
  private int maxSize;

  private final RedisPublisher redisPublisher;
  private final BatchWriter batchWriter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private LinkedBlockingQueue<ChatMessage> queue;
  private ScheduledExecutorService scheduler;

  /**
   * Constructs the BroadcastDLQ with its retry dependencies.
   *
   * @param redisPublisher publishes messages to Redis Pub/Sub for server fanout
   * @param batchWriter    buffers messages for batch DB persistence after successful retry
   */
  public BroadcastDLQ(RedisPublisher redisPublisher, BatchWriter batchWriter) {
    this.redisPublisher = redisPublisher;
    this.batchWriter = batchWriter;
  }

  /** Initializes the queue and starts the background retry task. */
  @PostConstruct
  public void start() {
    queue = new LinkedBlockingQueue<>(maxSize);
    scheduler = new ScheduledThreadPoolExecutor(1);
    scheduler.scheduleAtFixedRate(this::retryAll,
        retryIntervalMs, retryIntervalMs, TimeUnit.MILLISECONDS);
    log.info("BroadcastDLQ started: retryIntervalMs={}, maxSize={}", retryIntervalMs, maxSize);
  }

  /** Shuts down the retry scheduler on application stop. */
  @PreDestroy
  public void stop() {
    scheduler.shutdown();
    if (!queue.isEmpty()) {
      log.warn("BroadcastDLQ shutting down with {} undelivered messages", queue.size());
    }
  }

  /**
   * Adds a message that failed Redis broadcast to the retry queue.
   * If the queue is full, logs an error — the message cannot be recovered.
   *
   * @param msg the message to retry
   */
  public void add(ChatMessage msg) {
    if (!queue.offer(msg)) {
      log.error("BroadcastDLQ full ({}), message undeliverable: {}", maxSize, msg.getMessageId());
      return;
    }
    log.warn("Message queued for broadcast retry: {}", msg.getMessageId());
  }

  /**
   * Returns the current number of messages pending retry.
   *
   * @return queue size
   */
  public int size() { return queue.size(); }

  /**
   * Drains all queued messages and attempts to re-publish each to Redis.
   * On success: forwards to BatchWriter for DB persistence.
   * On failure: re-enqueues for the next retry interval.
   * Package-private for testing.
   */
  void retryAll() {
    List<ChatMessage> batch = new ArrayList<>();
    queue.drainTo(batch);
    if (batch.isEmpty()) return;

    log.info("BroadcastDLQ retrying {} messages", batch.size());

    for (ChatMessage msg : batch) {
      try {
        String payload = objectMapper.writeValueAsString(msg);
        redisPublisher.publish(msg.getRoomId(), payload);
        batchWriter.enqueue(msg);
        log.info("BroadcastDLQ retry succeeded for message: {}", msg.getMessageId());
      } catch (Exception e) {
        log.warn("BroadcastDLQ retry failed for message {}, re-queuing: {}",
            msg.getMessageId(), e.getMessage());
        add(msg);
      }
    }
  }
}
