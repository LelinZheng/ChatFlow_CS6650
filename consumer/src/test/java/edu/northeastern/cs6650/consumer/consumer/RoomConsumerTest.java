package edu.northeastern.cs6650.consumer.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import edu.northeastern.cs6650.consumer.model.ChatMessage;
import edu.northeastern.cs6650.consumer.redis.RedisPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;



class RoomConsumerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private Channel mockChannel;
  private RedisPublisher mockRedisPublisher;
  private RoomConsumer consumer;

  private String validPayload(String messageId, String roomId) throws Exception {
    ChatMessage msg = new ChatMessage(
        messageId, roomId, "1", "user1", "hello",
        Instant.now().toString(), "TEXT", "server-1", "127.0.0.1"
    );
    return mapper.writeValueAsString(msg);
  }

  private Delivery mockDelivery(long deliveryTag, String payload) {
    Envelope envelope = new Envelope(deliveryTag, false, "chat.exchange", "room.5");
    AMQP.BasicProperties props = new AMQP.BasicProperties();
    return new Delivery(envelope, props, payload.getBytes(StandardCharsets.UTF_8));
  }

  @BeforeEach
  void setUp() {
    mockChannel = mock(Channel.class);
    mockRedisPublisher = mock(RedisPublisher.class);
    consumer = new RoomConsumer(mockChannel, List.of("room.5"), mockRedisPublisher);
  }

  // ── successful delivery ────────────────────────────────────

  @Test
  void handleDelivery_validMessage_broadcastsAndAcks() throws Exception {
    String payload = validPayload("msg-1", "5");
    Delivery delivery = mockDelivery(1L, payload);

    consumer.handleDelivery(delivery);

    verify(mockRedisPublisher).publish("5", payload);
    verify(mockChannel).basicAck(1L, false);
    verify(mockChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }

  // ── duplicate detection ────────────────────────────────────

  @Test
  void handleDelivery_duplicateMessage_skipsAndAcks() throws Exception {
    String payload = validPayload("msg-dup", "5");
    Delivery delivery = mockDelivery(1L, payload);

    consumer.handleDelivery(delivery);
    consumer.handleDelivery(delivery);

    verify(mockRedisPublisher, times(1)).publish(any(), any());
    verify(mockChannel, times(2)).basicAck(anyLong(), eq(false));
  }

  // ── deserialization failure ────────────────────────────────

  @Test
  void handleDelivery_invalidJson_nacksWithoutRequeue() throws Exception {
    Delivery delivery = mockDelivery(1L, "{not valid json}");

    consumer.handleDelivery(delivery);

    verify(mockRedisPublisher, never()).publish(any(), any());
    verify(mockChannel).basicNack(1L, false, false);
  }

  // ── retry + nack on broadcast failure ─────────────────────

  @Test
  void handleDelivery_broadcastFailsAllRetries_nacks() throws Exception {
    doThrow(new RuntimeException("publish failed"))
        .when(mockRedisPublisher).publish(any(), any());

    String payload = validPayload("msg-2", "5");
    Delivery delivery = mockDelivery(2L, payload);

    consumer.handleDelivery(delivery);

    verify(mockRedisPublisher, times(3)).publish(any(), any());
    verify(mockChannel).basicNack(2L, false, false);
    verify(mockChannel, never()).basicAck(anyLong(), anyBoolean());
  }

  @Test
  void handleDelivery_broadcastFailsThenSucceeds_acks() throws Exception {
    doThrow(new RuntimeException("transient error"))
        .doNothing()
        .when(mockRedisPublisher).publish(any(), any());

    String payload = validPayload("msg-3", "5");
    Delivery delivery = mockDelivery(3L, payload);

    consumer.handleDelivery(delivery);

    verify(mockRedisPublisher, times(2)).publish(any(), any());
    verify(mockChannel).basicAck(3L, false);
    verify(mockChannel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
  }
}
