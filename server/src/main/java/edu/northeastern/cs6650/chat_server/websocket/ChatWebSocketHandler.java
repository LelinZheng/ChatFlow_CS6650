package edu.northeastern.cs6650.chat_server.websocket;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
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
 * Validates each message, wraps it in a {@link QueueMessage}, and publishes it
 * to the RabbitMQ topic exchange for the appropriate room. The actual broadcasting
 * to connected clients is handled by the consumer application.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;

  private final ChannelPool channelPool;

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

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
   *   <li>Publish a {@link QueueMessage} to RabbitMQ on routing key {@code room.{roomId}}</li>
   * </ol>
   * Validation or JSON errors are sent back to the client as {@link ErrorResponse}.
   *
   * @param session the sender's WebSocket session
   * @param message the raw text frame received
   * @throws IOException if sending an error response fails
   */
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {

    ClientMessage clientMessage;
    try {
      clientMessage = objectMapper.readValue(message.getPayload(),
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

    QueueMessage queueMsg = new QueueMessage(
        UUID.randomUUID().toString(),           // messageId
        clientMessage.getRoomId(),              // roomId
        clientMessage.getUserId(),              // userId
        clientMessage.getUsername(),            // username
        clientMessage.getMessage(),             // message
        Instant.now().toString(),               // timestamp
        clientMessage.getMessageType().name(),  // messageType
        session.getLocalAddress().toString(),   // serverId
        session.getRemoteAddress().toString()   // clientIp
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while borrowing channel");
    } catch (Exception e) {
      log.error("Failed to publish message to queue: {}", e.getMessage());
    } finally {
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
