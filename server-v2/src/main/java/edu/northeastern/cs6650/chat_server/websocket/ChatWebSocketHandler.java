package edu.northeastern.cs6650.chat_server.websocket;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import edu.northeastern.cs6650.chat_server.circuitbreaker.CircuitBreaker;
import edu.northeastern.cs6650.chat_server.config.ChannelPool;
import edu.northeastern.cs6650.chat_server.config.RabbitMQConfig;
import edu.northeastern.cs6650.chat_server.model.ClientMessage;
import edu.northeastern.cs6650.chat_server.model.ErrorResponse;
import edu.northeastern.cs6650.chat_server.model.QueueMessage;
import edu.northeastern.cs6650.chat_server.validation.MessageValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * WebSocket handler that processes incoming chat messages.
 * <p>
 * Clients connect to {@code /chat}. Room membership is not fixed at connect time —
 * every incoming message carries a {@code roomId}, and the connection is moved to
 * that room on each message. This supports load-test clients where many users share
 * a pool of connections and each message may target a different room.
 * <p>
 * All message types (JOIN, LEAVE, TEXT) follow the same path:
 * <ol>
 *   <li>Parse and validate the message</li>
 *   <li>Move the connection to the room from the message ({@link RoomManager#addSession})</li>
 *   <li>Publish to RabbitMQ so the consumer broadcasts it to all connections in that room</li>
 * </ol>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;

  private final ChannelPool channelPool;

  private final RoomManager roomManager;

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final CircuitBreaker circuitBreaker = new CircuitBreaker(
      5, 30_000);

  /**
   * Constructor for ChatWebSocketHandler.
   * @param objectMapper the ObjectMapper for JSON processing
   * @param channelPool  pool of RabbitMQ channels for publishing messages
   * @param roomManager  manages WebSocket sessions for broadcasting messages
   */
  public ChatWebSocketHandler(ObjectMapper objectMapper, ChannelPool channelPool,
      RoomManager roomManager) {
    this.channelPool = channelPool;
    this.objectMapper = objectMapper;
    this.roomManager = roomManager;
  }

  /**
   * Accepts the WebSocket connection. The session is not assigned to any room
   * until a {@code JOIN} message is received.
   *
   * @param session the newly established WebSocket session
   */
  public void afterConnectionEstablished(WebSocketSession session) {
    log.info("WebSocket connection established: {}", session.getId());
  }

  /**
   * Processes an incoming WebSocket text message.
   * <p>
   * All message types (JOIN, LEAVE, TEXT) go through the same path:
   * the connection is moved to the room from the message, then the message
   * is published to RabbitMQ so every connection in that room receives it.
   * JOIN and LEAVE carry text (e.g. "user joined") that is broadcast just like TEXT.
   *
   * @param session the sender's WebSocket session
   * @param message the raw text frame received
   * @throws IOException if sending an error response to the client fails
   */
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {

    // Parse JSON
    ClientMessage clientMessage;
    try {
      clientMessage = objectMapper.readValue(message.getPayload(), ClientMessage.class);
    } catch (Exception e) {
      ErrorResponse err = new ErrorResponse(
          "INVALID_JSON",
          "Malformed JSON payload",
          List.of(e.getMessage()));
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(err)));
      return;
    }

    // Validate fields
    List<String> errors = MessageValidator.validate(clientMessage);
    if (!errors.isEmpty()) {
      ErrorResponse errorResponse = new ErrorResponse(
          "VALIDATION_FAILED",
          "Message validation failed",
          errors);
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
      return;
    }

    // Move this connection to the room specified in the message (all message types)
    roomManager.addSession(clientMessage.getRoomId(), session);

    // Check circuit breaker before attempting to publish
    if (!circuitBreaker.allowRequest()) {
      log.warn("Circuit breaker open — dropping message for room {}", clientMessage.getRoomId());
      ErrorResponse circuitErr = new ErrorResponse(
          "SERVICE_UNAVAILABLE",
          "Message queue is currently unavailable, please try again later",
          List.of());
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(circuitErr)));
      return;
    }

    // Build queue message and publish for all types (JOIN/LEAVE carry broadcast text too)
    QueueMessage queueMsg = new QueueMessage(
        UUID.randomUUID().toString(),
        clientMessage.getRoomId(),
        clientMessage.getUserId(),
        clientMessage.getUsername(),
        clientMessage.getMessage(),
        Instant.now().toString(),
        clientMessage.getMessageType().name(),
        session.getLocalAddress().toString(),
        session.getRemoteAddress().toString()
    );

    String json = objectMapper.writeValueAsString(queueMsg);
    String routingKey = "room." + clientMessage.getRoomId();

    Channel channel = null;
    try {
      channel = channelPool.borrowChannel();
      channel.basicPublish(
          RabbitMQConfig.EXCHANGE_NAME,
          routingKey,
          MessageProperties.PERSISTENT_TEXT_PLAIN,
          json.getBytes(StandardCharsets.UTF_8)
      );
      circuitBreaker.recordSuccess();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while borrowing channel for session {}", session.getId());

    } catch (Exception e) {
      circuitBreaker.recordFailure();
      log.error("Failed to publish message to room {}: {}", clientMessage.getRoomId(),
          e.getMessage());
      ErrorResponse publishErr = new ErrorResponse(
          "PUBLISH_FAILED",
          "Failed to deliver message, please try again",
          List.of(e.getMessage()));
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(publishErr)));
    } finally {
      channelPool.returnChannel(channel);
    }
  }

  /**
   * Removes the client's WebSocket session from its room on disconnect.
   * Delegates to {@link RoomManager#removeSession} which looks up the room
   * from the session ID and cleans up both internal maps.
   *
   * @param session the closed WebSocket session
   * @param status  the close status code and reason
   */
  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    roomManager.removeSession(session);
    log.info("WebSocket connection closed: {}", session.getId());
  }

}
