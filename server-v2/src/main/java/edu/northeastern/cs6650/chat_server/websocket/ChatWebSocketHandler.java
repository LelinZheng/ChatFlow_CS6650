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
import java.util.concurrent.atomic.AtomicInteger;
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
 * Validates each message, wraps it in a {@link QueueMessage}, and publishes it
 * to the RabbitMQ topic exchange for the appropriate room. The actual broadcasting
 * to connected clients is handled by the consumer application.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;

  private final ChannelPool channelPool;

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final CircuitBreaker circuitBreaker = new CircuitBreaker(
      5, 30_000);

  /**
   * Constructor for ChatWebSocketHandler.
   * @param objectMapper the ObjectMapper for JSON processing
   * @param channelPool  pool of RabbitMQ channels for publishing messages
   */
  public ChatWebSocketHandler(ObjectMapper objectMapper, ChannelPool channelPool) {
     this.channelPool = channelPool;
    this.objectMapper = objectMapper;
  }

  /**
   * Logs new WebSocket connections.
   *
   * @param session the newly established WebSocket session
   */
  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println("New WebSocket connection established: " + session.getId());
  }

  /**
   * Processes an incoming WebSocket text message.
   * <p>
   * Steps:
   * <ol>
   *   <li>Deserialize JSON payload into {@link ClientMessage}</li>
   *   <li>Validate fields via {@link MessageValidator}</li>
   *   <li>Check the circuit breaker — if OPEN or HALF_OPEN (non-probe thread),
   *       reject immediately with {@code SERVICE_UNAVAILABLE}</li>
   *   <li>Publish a {@link QueueMessage} to RabbitMQ on routing key {@code room.{roomId}}</li>
   * </ol>
   * Validation or JSON errors are sent back to the client as {@link ErrorResponse}.
   * Publish failures are reported as {@code PUBLISH_FAILED} and recorded in the circuit breaker.
   * After 5 consecutive failures the circuit opens; after 30 seconds one probe is allowed
   * through — if it succeeds the circuit closes and normal operation resumes automatically.
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

    // Build queue message
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

    // Publish to RabbitMQ
    Channel channel = null;
    try {
      channel = channelPool.borrowChannel();
      channel.basicPublish(
          RabbitMQConfig.EXCHANGE_NAME,
          routingKey,
          MessageProperties.PERSISTENT_TEXT_PLAIN,
          json.getBytes(StandardCharsets.UTF_8)
      );
      circuitBreaker.recordSuccess(); // reset failure count on success

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while borrowing channel for session {}", session.getId());

    } catch (Exception e) {
      circuitBreaker.recordFailure();
      log.error("Failed to publish message to room {}: {}", clientMessage.getRoomId(),
          e.getMessage());
      // Notify client that their message was not delivered
      ErrorResponse publishErr = new ErrorResponse(
          "PUBLISH_FAILED",
          "Failed to deliver message, please try again",
          List.of(e.getMessage()));
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(publishErr)));
    } finally {
      // Always return channel to pool regardless of success or failure
      channelPool.returnChannel(channel);
    }
  }

  /**
   * Logs WebSocket connection closures.
   *
   * @param session the closed WebSocket session
   * @param status  the close status code and reason
   */
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    System.out.println("WebSocket connection closed: " + session.getId());
  }

}
