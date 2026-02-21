package edu.northeastern.cs6650.chat_server.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

public class ChatWebSocketHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void handleTextMessage_invalidJson_sendsInvalidJsonError() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper);
    WebSocketSession session = mock(WebSocketSession.class);

    handler.handleTextMessage(session, new TextMessage("{not valid json"));

    TextMessage sent = captureSentTextMessage(session);
    JsonNode root = mapper.readTree(sent.getPayload());

    assertEquals("ERROR", root.get("status").asText());
    assertEquals("INVALID_JSON", root.get("errorCode").asText());
    assertEquals("Malformed JSON payload", root.get("message").asText());
    assertTrue(root.get("details").isArray());
    assertTrue(root.get("details").size() >= 1);
  }

  @Test
  void handleTextMessage_validationFailed_sendsValidationFailedError() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper);
    WebSocketSession session = mock(WebSocketSession.class);

    String payload = """
        {
          "userId": "123",
          "username": "ab",
          "message": "hi",
          "timestamp": "bad-ts",
          "messageType": "TEXT"
        }
        """;

    handler.handleTextMessage(session, new TextMessage(payload));

    TextMessage sent = captureSentTextMessage(session);
    JsonNode root = mapper.readTree(sent.getPayload());

    assertEquals("ERROR", root.get("status").asText());
    assertEquals("VALIDATION_FAILED", root.get("errorCode").asText());
    assertEquals("Message validation failed", root.get("message").asText());
    assertTrue(root.get("details").isArray());
    String details = root.get("details").toString();
    assertTrue(details.contains("username must be 3-20 alphanumeric characters"));
    assertTrue(details.contains("timestamp must be in ISO 8601 format"));
  }

  @Test
  void handleTextMessage_validTextMessage_sendsOkEcho() throws Exception {
    ChatWebSocketHandler handler = new ChatWebSocketHandler(mapper);
    WebSocketSession session = mock(WebSocketSession.class);

    String payload = """
        {
          "userId": "123",
          "username": "user_123",
          "message": "hello",
          "timestamp": "%s",
          "messageType": "TEXT"
        }
        """.formatted(Instant.now().toString());

    handler.handleTextMessage(session, new TextMessage(payload));

    TextMessage sent = captureSentTextMessage(session);
    JsonNode root = mapper.readTree(sent.getPayload());

    assertEquals("OK", root.get("status").asText());
    assertEquals("hello", root.get("message").asText());

    assertNotNull(root.get("serverTimestamp"));
    assertDoesNotThrow(() -> Instant.parse(root.get("serverTimestamp").asText()));
  }

  private TextMessage captureSentTextMessage(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, times(1)).sendMessage(captor.capture());
    return captor.getValue();
  }
}
