package edu.northeastern.cs6650.client.model;


/**
 * Represents a single chat message sent from the client to the WebSocket server.
 *
 * <p>This object models the JSON payload required by the server API, including
 * user identity, message content, message type, and timestamp.</p>
 *
 * <p>All fields including {@code messageId} and {@code roomId} are included
 * in the JSON payload sent to the server, as the server requires them for
 * deduplication and room routing.</p>
 */
public class ChatMessage {
  private String messageId;
  private String userId;
  private String username;
  private String message;
  private MessageType messageType;
  private String timestamp;
  private String roomId;

  /**
   * Default constructor.
   */
  public ChatMessage() {
  }

  /**
   * Parameterized constructor.
   * @param userId
   * @param username
   * @param message
   * @param messageType
   * @param timestamp
   * @param roomId
   */
  public ChatMessage(String userId, String username, String message, MessageType messageType,
      String timestamp, String roomId) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.messageType = messageType;
    this.timestamp = timestamp;
    this.roomId = roomId;
  }

  /**
   * Get user ID.
   * @return
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Set user ID.
   * @param userId
   */
  public void setUserId(String userId) {
    this.userId = userId;
  }

  /**
   * Get username.
   * @return
   */
  public String getUsername() {
    return username;
  }

  /**
   * Set username.
   * @param username
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Get message.
   * @return
   */
  public String getMessage() {
    return message;
  }

  /**
   * Set message.
   * @param message
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Get message type.
   * @return
   */
  public MessageType getMessageType() {
    return messageType;
  }

  /**
   * Set message type.
   * @param messageType
   */
  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  /**
   * Get timestamp.
   * @return
   */
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Set timestamp.
   * @param timestamp
   */
  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Get message ID.
   * @return
   */
  public String getMessageId() {
    return messageId;
  }

  /**
   * Set message ID.
   * @param messageId
   */
  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  /**
   * Get room ID.
   * @return
   */
  public String getRoomId() {
    return roomId;
  }

  /**
   * Set room ID.
   * @param roomId
   */
  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  /**
   * Check if message is poison pill.
   * @return
   */
  public boolean isPoison() {
    return "POISON".equals(messageId);
  }

  /**
   * Create poison pill message.
   * @return
   */
  public static ChatMessage poison() {
    ChatMessage m = new ChatMessage();
    m.messageId = "POISON";
    return m;
  }
}
