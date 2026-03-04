package edu.northeastern.cs6650.consumer.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes chat messages to Redis Pub/Sub channels so that all WebSocket
 * server instances receive them simultaneously.
 * <p>
 * Each room maps to a dedicated channel {@code chat:room:{roomId}}.
 * The payload published is the raw JSON that WebSocket clients expect to
 * receive — no extra wrapping is added.
 */
@Service
public class RedisPublisher {

  private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);
  private static final String CHANNEL_PREFIX = "chat:room:";

  private final StringRedisTemplate redisTemplate;

  public RedisPublisher(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Publishes {@code payload} to the Redis channel for {@code roomId}.
   * All subscribed WebSocket servers will receive and broadcast the message.
   *
   * @param roomId  the room the message belongs to
   * @param payload the raw JSON string to deliver to WebSocket clients
   */
  public void publish(String roomId, String payload) {
    String channel = CHANNEL_PREFIX + roomId;
    redisTemplate.convertAndSend(channel, payload);
    log.debug("Published to Redis channel {}", channel);
  }
}
