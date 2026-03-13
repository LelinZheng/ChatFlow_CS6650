package edu.northeastern.cs6650.chat_server.redis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs6650.chat_server.websocket.RoomManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class RedisSubscriberTest {

  private final RoomManager mockRoomManager = mock(RoomManager.class);
  private final RedisSubscriber subscriber = new RedisSubscriber(mockRoomManager);

  private Message mockMessage(String channel, String body) {
    Message message = mock(Message.class);
    when(message.getChannel()).thenReturn(channel.getBytes(StandardCharsets.UTF_8));
    when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
    return message;
  }

  @Test
  void onMessage_extractsRoomIdFromChannelAndBroadcasts() {
    String payload = "{\"messageId\":\"m1\",\"roomId\":\"5\",\"message\":\"hello\"}";
    Message message = mockMessage("chat:room:5", payload);

    subscriber.onMessage(message, null);

    verify(mockRoomManager).broadcastToRoom("5", payload);
  }

  @Test
  void onMessage_differentRoomId_broadcastsToCorrectRoom() {
    String payload = "{\"messageId\":\"m2\",\"roomId\":\"12\",\"message\":\"hi\"}";
    Message message = mockMessage("chat:room:12", payload);

    subscriber.onMessage(message, null);

    verify(mockRoomManager).broadcastToRoom("12", payload);
  }
}
