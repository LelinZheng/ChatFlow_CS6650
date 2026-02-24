package edu.northeastern.cs6650.consumer.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import edu.northeastern.cs6650.consumer.model.ChatMessage;
import edu.northeastern.cs6650.consumer.websocket.RoomSessionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * A single consumer thread that subscribes to assigned RabbitMQ room queues,
 * broadcasts messages to connected WebSocket clients, and acknowledges delivery.
 * <p>
 * Each instance owns a dedicated RabbitMQ channel. On {@link #run()}, it registers
 * push-based delivery callbacks via {@code basicConsume} for each assigned queue,
 * then enters a sleep loop to stay alive while RabbitMQ fires callbacks asynchronously.
 * <p>
 * Delivery guarantees:
 * <ul>
 *   <li>At-least-once: only acknowledges after successful broadcast</li>
 *   <li>Retry with exponential backoff: up to 3 attempts on broadcast failure</li>
 *   <li>Duplicate detection: messageId cache prevents reprocessing redelivered messages</li>
 *   <li>Dead session cleanup: handled inside {@link RoomSessionHandler#broadcastToRoom}</li>
 * </ul>
 */
public class RoomConsumer implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(RoomConsumer.class);

  private static final int MAX_RETRIES = 3;
  private static final long BASE_BACKOFF_MS = 100;

  private final Channel channel;
  private final List<String> assignedQueues;
  private final RoomSessionHandler roomSessionHandler;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // duplicate detection: messageId -> time delivered
  private final ConcurrentHashMap<String, Long> recentlyDelivered = new ConcurrentHashMap<>();

  /**
   * Constructs a consumer for the given set of queues.
   *
   * @param channel            dedicated RabbitMQ channel for this thread
   * @param assignedQueues     list of queue names this consumer subscribes to (e.g. "room.1")
   * @param roomSessionHandler used to broadcast messages to connected WebSocket sessions
   */
  public RoomConsumer(Channel channel, List<String> assignedQueues,
      RoomSessionHandler roomSessionHandler) {
    this.channel = channel;
    this.assignedQueues = assignedQueues;
    this.roomSessionHandler = roomSessionHandler;
  }

  /**
   * Registers delivery callbacks for all assigned queues, then enters a sleep
   * loop to keep the thread alive. RabbitMQ fires {@link #handleDelivery} on its
   * internal thread whenever a message arrives. The sleep loop also periodically
   * evicts old entries from the duplicate detection cache.
   * <p>
   * Exits cleanly when interrupted via {@link Thread#interrupt()}.
   */
  @Override
  public void run() {
    try {
      // subscribe to all assigned queues on this channel
      for (String queue : assignedQueues) {
        channel.basicConsume(queue, false, // false = manual ack
            (consumerTag, delivery) -> handleDelivery(delivery),
            consumerTag -> log.warn("Consumer cancelled for tag: {}", consumerTag));
      }
      log.info("RoomConsumer subscribed to: {}", assignedQueues);

      // enters sleep loop to stay alive and periodically evict old entries from the duplicate cache
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(1000);
        evictOldEntries(); // periodic cleanup of duplicate cache
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.info("RoomConsumer interrupted, shutting down");
    } catch (Exception e) {
      log.error("RoomConsumer failed: {}", e.getMessage(), e);
    }
  }

  /**
   * Processes a single delivered message from RabbitMQ.
   * Deserializes the payload, checks for duplicates, then delegates to
   * {@link #broadcastWithRetry}. Nacks the message if deserialization fails.
   *
   * @param delivery the raw RabbitMQ delivery containing headers and body
   */
  void handleDelivery(Delivery delivery) {
    long deliveryTag = delivery.getEnvelope().getDeliveryTag();

    try {
      ChatMessage msg = objectMapper.readValue(delivery.getBody(), ChatMessage.class);

      // duplicate check
      if (isDuplicate(msg.getMessageId())) {
        log.debug("Duplicate message {} detected, skipping", msg.getMessageId());
        channel.basicAck(deliveryTag, false); // ack so it leaves the queue
        return;
      }

      String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
      broadcastWithRetry(msg.getRoomId(), payload, deliveryTag);

    } catch (Exception e) {
      log.error("Failed to process delivery {}: {}", deliveryTag, e.getMessage());
      nack(deliveryTag);
    }
  }

  /**
   * Broadcasts a message to all WebSocket sessions in the target room,
   * retrying up to {@link #MAX_RETRIES} times with exponential backoff on failure.
   * Acknowledges to RabbitMQ on success, nacks without requeue after all retries exhausted.
   *
   * @param roomId      the room to broadcast to
   * @param payload     the raw JSON string to send to clients
   * @param deliveryTag RabbitMQ delivery tag used for ack/nack
   */
  private void broadcastWithRetry(String roomId, String payload, long deliveryTag) {
    int attempt = 0;
    while (attempt < MAX_RETRIES) {
      try {
        roomSessionHandler.broadcastToRoom(roomId, payload);
        ack(deliveryTag); // success — tell RabbitMQ we're done
        return;
      } catch (Exception e) {
        attempt++;
        if (attempt == MAX_RETRIES) {
          log.error("Broadcast to room {} failed after {} attempts", roomId, MAX_RETRIES);
          nack(deliveryTag); // give up — RabbitMQ will discard (no requeue)
          return;
        }
        long backoff = BASE_BACKOFF_MS * (1L << attempt); // 200ms, 400ms, 800ms
        log.warn("Broadcast attempt {} failed for room {}, retrying in {}ms",
            attempt, roomId, backoff);
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          nack(deliveryTag);
          return;
        }
      }
    }
  }

  /**
   * Acknowledges a message to RabbitMQ, signaling successful processing.
   *
   * @param deliveryTag the delivery tag of the message to acknowledge
   */
  private void ack(long deliveryTag) {
    try {
      channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
      log.error("Failed to ack delivery {}: {}", deliveryTag, e.getMessage());
    }
  }

  /**
   * Negatively acknowledges a message without requeuing, discarding it after
   * all retry attempts are exhausted.
   *
   * @param deliveryTag the delivery tag of the message to reject
   */
  private void nack(long deliveryTag) {
    try {
      channel.basicNack(deliveryTag, false, false); // false = don't requeue
    } catch (Exception e) {
      log.error("Failed to nack delivery {}: {}", deliveryTag, e.getMessage());
    }
  }

  /**
   * Checks whether a message has already been delivered, using a time-bounded cache.
   * Uses {@code putIfAbsent} for atomic check-and-insert with no locking.
   *
   * @param messageId the UUID of the message to check
   * @return true if the message was already delivered, false if it is new
   */
  private boolean isDuplicate(String messageId) {
    if (messageId == null) return false;
    return recentlyDelivered.putIfAbsent(
        messageId, System.currentTimeMillis()) != null;
  }

  /**
   * Removes entries older than 60 seconds from the duplicate detection cache.
   * Called periodically from the sleep loop in {@link #run()} to prevent
   * unbounded memory growth.
   */
  private void evictOldEntries() {
    long cutoff = System.currentTimeMillis() - 60_000; // evict after 60s
    recentlyDelivered.entrySet().removeIf(e -> e.getValue() < cutoff);
  }
}
