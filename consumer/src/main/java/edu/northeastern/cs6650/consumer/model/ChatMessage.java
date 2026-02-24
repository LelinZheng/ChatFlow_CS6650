package edu.northeastern.cs6650.consumer.model;

/**
 * Represents a message published to the RabbitMQ queue.
 * <p>
 * Messages published to the RabbitMQ queue are represented by this class.
 * The consumer reads this format to broadcast messages to room participants.
 */
public class ChatMessage {
  private String messageId;
  private String roomId;
  private String userId;
  private String username;
  private String message;
  private String timestamp;
  private String messageType;
  private String serverId;
  private String clientIp;

  /**
   * Constructor for ChatMessage.
   * @param messageId   unique UUID for this message
   * @param roomId      destination room (1–20)
   * @param userId      sender's user ID
   * @param username    sender's display name
   * @param message     message body
   * @param timestamp   server-side ISO-8601 timestamp
   * @param messageType TEXT, JOIN, or LEAVE
   * @param serverId    local address of the WebSocket server
   * @param clientIp    remote address of the sending client
   */
  public ChatMessage(String messageId, String roomId, String userId, String username,
      String message,
      String timestamp, String messageType, String serverId, String clientIp) {
    this.messageId = messageId;
    this.roomId = roomId;
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.timestamp = timestamp;
    this.messageType = messageType;
    this.serverId = serverId;
    this.clientIp = clientIp;
  }

  /**
   * Default constructor for ChatMessage (required for JSON deserialization).
   */
  public ChatMessage() {
  }

  /**
   * Get the unique message ID.
   * @return messageId string
   */
  public String getMessageId() {
    return messageId;
  }

  /**
   * Get the room ID this message is associated with.
   * @return roomId string
   */
  public String getRoomId() {
    return roomId;
  }

  /**
   * Get the user ID of the message sender.
   * @return userId string
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Get the username of the message sender.
   * @return username string
   */
  public String getUsername() {
    return username;
  }

  /**
   * Get the message content.
   * @return message string
   */
  public String getMessage() {
    return message;
  }

  /**
   * Get the server-side timestamp when the message was processed.
   * @return timestamp string in ISO-8601 format
   */
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Get the type of the message (e.g., TEXT, JOIN, LEAVE).
   * @return messageType string
   */
  public String getMessageType() {
    return messageType;
  }

  /**
   * Get the local address of the WebSocket server that processed this message.
   * @return serverId string
   */
  public String getServerId() {
    return serverId;
  }

  /**
   * Get the remote address of the client that sent this message.
   * @return clientIp string
   */
  public String getClientIp() {
    return clientIp;
  }
}
