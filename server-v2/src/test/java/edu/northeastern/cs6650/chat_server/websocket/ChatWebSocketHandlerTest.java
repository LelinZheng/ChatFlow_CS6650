package edu.northeastern.cs6650.chat_server.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import edu.northeastern.cs6650.chat_server.config.ChannelPool;
import edu.northeastern.cs6650.chat_server.config.RabbitMQConfig;
import java.net.InetSocketAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

public class ChatWebSocketHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  /** Builds a mock ChannelPool that returns a mock Channel. */
  private ChannelPool mockChannelPool() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);
    return mockPool;
  }

  private String validPayload() {
    return """
            {
              "userId": "123",
              "username": "user_123",
              "message": "hello",
              "timestamp": "%s",
              "messageType": "TEXT",
              "roomId": "5"
            }
            """.formatted(Instant.now().toString());
  }

  @Test
  void handleTextMessage_invalidJson_sendsInvalidJsonError() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool());
    WebSocketSession session = mock(WebSocketSession.class);

    handler.handleTextMessage(session, new TextMessage("{not valid json"));

    TextMessage sent = captureOneSentMessage(session);
    JsonNode root = mapper.readTree(sent.getPayload());

    assertEquals("ERROR", root.get("status").asText());
    assertEquals("INVALID_JSON", root.get("errorCode").asText());
    assertEquals("Malformed JSON payload", root.get("message").asText());
    assertTrue(root.get("details").isArray());
    assertTrue(root.get("details").size() >= 1);
  }

  @Test
  void handleTextMessage_validationFailed_sendsValidationFailedError() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool());
    WebSocketSession session = mock(WebSocketSession.class);

    // username too short, bad timestamp, missing roomId
    String payload = """
            {
              "userId": "123",
              "username": "ab",
              "message": "hi",
              "timestamp": "bad-ts",
              "messageType": "TEXT",
              "roomId": "5"
            }
            """;

    handler.handleTextMessage(session, new TextMessage(payload));

    TextMessage sent = captureOneSentMessage(session);
    JsonNode root = mapper.readTree(sent.getPayload());

    assertEquals("ERROR", root.get("status").asText());
    assertEquals("VALIDATION_FAILED", root.get("errorCode").asText());
    assertEquals("Message validation failed", root.get("message").asText());

    String details = root.get("details").toString();
    assertTrue(details.contains("username must be 3-20 alphanumeric characters"));
    assertTrue(details.contains("timestamp must be in ISO-8601 format"));
  }

  @Test
  void handleTextMessage_validMessage_publishesToRabbitMQ() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);

    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockPool);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getLocalAddress()).thenReturn(new InetSocketAddress("localhost", 8080));
    when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 54321));

    handler.handleTextMessage(session, new TextMessage(validPayload()));

    // Handler should NOT send anything back to the client
    verify(session, never()).sendMessage(any());

    // Handler should publish to RabbitMQ on the correct routing key
    verify(mockChannel, times(1)).basicPublish(
        eq(RabbitMQConfig.EXCHANGE_NAME),
        eq("room.5"),
        eq(MessageProperties.PERSISTENT_TEXT_PLAIN),
        any(byte[].class)
    );

    // Channel must be returned to pool
    verify(mockPool, times(1)).returnChannel(mockChannel);
  }

  @Test
  void handleTextMessage_validMessage_returnsChannelEvenOnPublishFailure() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);
    doThrow(new RuntimeException("publish failed"))
        .when(mockChannel).basicPublish(any(), any(), any(), any());

    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockPool);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getLocalAddress()).thenReturn(
        new InetSocketAddress("localhost", 8080));
    when(session.getRemoteAddress()).thenReturn(
        new InetSocketAddress("127.0.0.1", 54321));

    // Should not throw even if publish fails
    assertDoesNotThrow(() ->
        handler.handleTextMessage(session, new TextMessage(validPayload())));

    // Channel must still be returned to pool despite the exception
    verify(mockPool, times(1)).returnChannel(mockChannel);
  }

  private TextMessage captureOneSentMessage(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, times(1)).sendMessage(captor.capture());
    return captor.getValue();
  }
}