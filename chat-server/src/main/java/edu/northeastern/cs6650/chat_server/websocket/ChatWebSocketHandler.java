package edu.northeastern.cs6650.chat_server.websocket;

import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;

  public ChatWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println("New WebSocket connection established: " + session.getId());
  }

  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {

    // Echo back safely (payload as a JSON string)
    ObjectNode resp = objectMapper.createObjectNode();
    resp.put("status", "OK");
    resp.put("serverTimestamp", Instant.now().toString());
    resp.put("echo", message.getPayload());

    session.sendMessage(
        new TextMessage( objectMapper.writeValueAsString(resp)));
  }

  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    System.out.println("WebSocket connection closed: " + session.getId());
  }

}
