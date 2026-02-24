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

  public RoomConsumer(Channel channel, List<String> assignedQueues,
      RoomSessionHandler roomSessionHandler) {
    this.channel = channel;
    this.assignedQueues = assignedQueues;
    this.roomSessionHandler = roomSessionHandler;
  }

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

  private void handleDelivery(Delivery delivery) {
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

  private void ack(long deliveryTag) {
    try {
      channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
      log.error("Failed to ack delivery {}: {}", deliveryTag, e.getMessage());
    }
  }

  private void nack(long deliveryTag) {
    try {
      channel.basicNack(deliveryTag, false, false); // false = don't requeue
    } catch (Exception e) {
      log.error("Failed to nack delivery {}: {}", deliveryTag, e.getMessage());
    }
  }

  private boolean isDuplicate(String messageId) {
    if (messageId == null) return false;
    return recentlyDelivered.putIfAbsent(
        messageId, System.currentTimeMillis()) != null;
  }

  private void evictOldEntries() {
    long cutoff = System.currentTimeMillis() - 60_000; // evict after 60s
    recentlyDelivered.entrySet().removeIf(e -> e.getValue() < cutoff);
  }
}
