package edu.northeastern.cs6650.chat_server.redis;

import edu.northeastern.cs6650.chat_server.websocket.RoomManager;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Listens to Redis Pub/Sub channels matching {@code chat:room:*} and
 * broadcasts received payloads to locally connected WebSocket clients.
 * <p>
 * The room ID is encoded in the channel name (e.g. {@code chat:room:5}),
 * so the raw payload is forwarded directly to {@link RoomManager#broadcastToRoom}
 * without any JSON parsing.
 */
@Component
public class RedisSubscriber implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(RedisSubscriber.class);
  private static final String CHANNEL_PREFIX = "chat:room:";

  private final RoomManager roomManager;

  public RedisSubscriber(RoomManager roomManager) {
    this.roomManager = roomManager;
  }

  /**
   * Called by Spring's Redis listener container when a message arrives on a
   * subscribed channel. Extracts the room ID from the channel name and
   * delegates broadcast to {@link RoomManager}.
   *
   * @param message the Redis message containing the channel and payload
   * @param pattern the matched pattern bytes (unused)
   */
  @Override
  public void onMessage(Message message, byte[] pattern) {
    String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    String roomId = channel.substring(CHANNEL_PREFIX.length());

    log.debug("Redis message on channel {} → broadcasting to room {}", channel, roomId);
    roomManager.broadcastToRoom(roomId, payload);
  }
}
