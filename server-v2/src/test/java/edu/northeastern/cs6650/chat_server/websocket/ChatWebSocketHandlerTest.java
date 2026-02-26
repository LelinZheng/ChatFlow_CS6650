package edu.northeastern.cs6650.chat_server.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import edu.northeastern.cs6650.chat_server.circuitbreaker.CircuitBreaker;
import edu.northeastern.cs6650.chat_server.config.ChannelPool;
import edu.northeastern.cs6650.chat_server.config.RabbitMQConfig;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

class ChatWebSocketHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private RoomManager mockRoomManager;

  @BeforeEach
  void setUp() {
    mockRoomManager = mock(RoomManager.class);
  }

  private ChannelPool mockChannelPool() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);
    return mockPool;
  }

  private WebSocketSession mockSession() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(UUID.randomUUID().toString());
    when(session.getUri()).thenReturn(new URI("ws://localhost:8080/chat/5"));
    when(session.getLocalAddress()).thenReturn(new InetSocketAddress("localhost", 8080));
    when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 54321));
    return session;
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

  // ── connection lifecycle ───────────────────────────────────

  @Test
  void afterConnectionEstablished_validRoom_addsSessionToRoomManager() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool(), mockRoomManager);
    WebSocketSession session = mockSession();

    handler.afterConnectionEstablished(session);

    verify(mockRoomManager).addSession("5", session);
  }

  @Test
  void afterConnectionEstablished_invalidUri_closesSession() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool(), mockRoomManager);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getUri()).thenReturn(null); // simulate bad URI

    handler.afterConnectionEstablished(session);

    verify(session).close();
    verify(mockRoomManager, never()).addSession(any(), any());
  }

  @Test
  void afterConnectionClosed_removesSessionFromRoomManager() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool(), mockRoomManager);
    WebSocketSession session = mockSession();

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(mockRoomManager).removeSession(session);
  }

  // ── JSON / validation errors ───────────────────────────────

  @Test
  void handleTextMessage_invalidJson_sendsInvalidJsonError() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool(), mockRoomManager);
    WebSocketSession session = mockSession();

    handler.handleTextMessage(session, new TextMessage("{not valid json"));

    JsonNode root = mapper.readTree(captureOneSentMessage(session).getPayload());
    assertEquals("INVALID_JSON", root.get("errorCode").asText());
  }

  @Test
  void handleTextMessage_validationFailed_sendsValidationFailedError() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockChannelPool(), mockRoomManager);
    WebSocketSession session = mockSession();

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

    JsonNode root = mapper.readTree(captureOneSentMessage(session).getPayload());
    assertEquals("VALIDATION_FAILED", root.get("errorCode").asText());
    String details = root.get("details").toString();
    assertTrue(details.contains("username must be 3-20 alphanumeric characters"));
    assertTrue(details.contains("timestamp must be in ISO-8601 format"));
  }

  // ── successful publish ─────────────────────────────────────

  @Test
  void handleTextMessage_validMessage_publishesToRabbitMQ() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);

    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockPool, mockRoomManager);
    WebSocketSession session = mockSession();

    handler.handleTextMessage(session, new TextMessage(validPayload()));

    verify(session, never()).sendMessage(any());
    verify(mockChannel).basicPublish(
        eq(RabbitMQConfig.EXCHANGE_NAME),
        eq("room.5"),
        eq(MessageProperties.PERSISTENT_TEXT_PLAIN),
        any(byte[].class)
    );
    verify(mockPool).returnChannel(mockChannel);
  }

  @Test
  void handleTextMessage_validMessage_returnsChannelEvenOnPublishFailure() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);
    doThrow(new RuntimeException("publish failed"))
        .when(mockChannel).basicPublish(any(), any(), any(), any());

    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockPool, mockRoomManager);
    WebSocketSession session = mockSession();

    assertDoesNotThrow(() -> handler.handleTextMessage(session, new TextMessage(validPayload())));
    verify(mockPool).returnChannel(mockChannel);
  }

  // ── circuit breaker ────────────────────────────────────────

  @Test
  void handleTextMessage_publishFailure_sendsPublishFailedError() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);
    doThrow(new RuntimeException("connection refused"))
        .when(mockChannel).basicPublish(any(), any(), any(), any());

    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockPool, mockRoomManager);
    WebSocketSession session = mockSession();

    handler.handleTextMessage(session, new TextMessage(validPayload()));

    JsonNode root = mapper.readTree(captureOneSentMessage(session).getPayload());
    assertEquals("PUBLISH_FAILED", root.get("errorCode").asText());
  }

  @Test
  void handleTextMessage_afterCircuitOpens_sendsServiceUnavailable() throws Exception {
    Channel mockChannel = mock(Channel.class);
    ChannelPool mockPool = mock(ChannelPool.class);
    when(mockPool.borrowChannel()).thenReturn(mockChannel);
    doThrow(new RuntimeException("rabbitmq down"))
        .when(mockChannel).basicPublish(any(), any(), any(), any());

    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper, mockPool, mockRoomManager);

    for (int i = 0; i < 5; i++) {
      handler.handleTextMessage(mockSession(), new TextMessage(validPayload()));
    }

    WebSocketSession session = mockSession();
    handler.handleTextMessage(session, new TextMessage(validPayload()));

    JsonNode root = mapper.readTree(captureOneSentMessage(session).getPayload());
    assertEquals("SERVICE_UNAVAILABLE", root.get("errorCode").asText());
  }

  // ── helper ────────────────────────────────────────────────

  private TextMessage captureOneSentMessage(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, times(1)).sendMessage(captor.capture());
    return captor.getValue();
  }
}