package edu.northeastern.cs6650.chat_server.websocket;

import edu.northeastern.cs6650.chat_server.model.ClientMessage;
import edu.northeastern.cs6650.chat_server.model.ErrorResponse;
import edu.northeastern.cs6650.chat_server.model.ServerEchoResponse;
import edu.northeastern.cs6650.chat_server.validation.MessageValidator;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

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

    try {
      ClientMessage clientMessage = objectMapper.readValue(message.getPayload(),
          ClientMessage.class);
    } catch (Exception e){
      ErrorResponse err = new ErrorResponse(
          "INVALID_JSON",
          "Malformed JSON payload",
          List.of(e.getMessage()));
      session.sendMessage(
          new TextMessage(objectMapper.writeValueAsString(err))
      );
      return;
    }

    ClientMessage clientMessage = objectMapper.readValue(message.getPayload(),
        ClientMessage.class);
    List<String> errors = MessageValidator.validate(clientMessage);

    if (!errors.isEmpty()){
      ErrorResponse errorResponse = new ErrorResponse(
          "VALIDATION_FAILED",
          "Message validation failed",
          errors
      );
      session.sendMessage(
          new TextMessage(objectMapper.writeValueAsString(errorResponse))
      );
      return;
    }

    ServerEchoResponse echoResponse = new ServerEchoResponse(
        "OK",
        clientMessage.getMessage(),
        Instant.now().toString()
    );
    session.sendMessage(
        new TextMessage(objectMapper.writeValueAsString(echoResponse))
    );
  }

  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    System.out.println("WebSocket connection closed: " + session.getId());
  }

}
