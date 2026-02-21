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

/**
 * WebSocket handler responsible for processing chat messages.
 * <p>
 * Handles incoming text messages, deserializes client payloads,
 * applies validation, and sends responses back to connected clients.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;

  /**
   * Constructor for ChatWebSocketHandler.
   * @param objectMapper the ObjectMapper for JSON processing
   */
  public ChatWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Handle new WebSocket connection establishment.
   * @param session the WebSocket session
   */
  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println("New WebSocket connection established: " + session.getId());
  }

  /**
   * Handle incoming text messages from clients.
   * @param session the WebSocket session
   * @param message the incoming text message
   * @throws IOException if an I/O error occurs
   */
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

  /**
   * Handle WebSocket connection closure.
   * @param session the WebSocket session
   * @param status the close status
   */
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    System.out.println("WebSocket connection closed: " + session.getId());
  }

}
