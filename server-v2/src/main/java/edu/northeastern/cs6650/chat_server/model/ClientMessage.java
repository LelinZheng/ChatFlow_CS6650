package edu.northeastern.cs6650.chat_server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a message sent by a client over WebSocket.
 * <p>
 * This object is used to deserialize incoming WebSocket messages
 * and encapsulates metadata such as sender identity, message type,
 * and timestamp and roomId.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientMessage {
  private String userId;
  private String username;
  private String message;
  private Messagetype messageType;
  private String timestamp;
  private String roomId;

  /** Default no-arg constructor required by Jackson for deserialization. */
  public ClientMessage() { }

  /**
   * Get the user ID of the message sender.
   * @return userId string
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Set the user ID of the message sender.
   * @param userId the user ID to set
   */
  public void setUserId(String userId) {
    this.userId = userId;
  }

  /**
   * Get the username of the message sender.
   * @return username string
   */
  public String getUsername() {
    return username;
  }

  /**
   * Set the username of the message sender.
   * @param username the username to set
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Get the message content sent by the client.
   * @return message string
   */
  public String getMessage() {
    return message;
  }

  /**
   * Set the message content sent by the client.
   * @param message the message content to set
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Get the type of the message (e.g., TEXT, JOIN, LEAVE).
   * @return messageType enum value
   */
  public Messagetype getMessageType() {
    return messageType;
  }

  /**
   * Set the type of the message (e.g., TEXT, JOIN, LEAVE).
   * @param messageType the message type to set
   */
  public void setMessageType(Messagetype messageType) {
    this.messageType = messageType;
  }

  /**
   * Get the timestamp of when the message was sent by the client.
   * @return timestamp string in ISO 8601 format
   */
  public String getTimestamp() {
    return timestamp;
  }

  /**
   * Set the timestamp of when the message was sent by the client.
   * @param timestamp the timestamp to set, expected in ISO 8601 format
   */
  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Get the room ID associated with the message.
   * @return roomId string
   */
  public String getRoomId() {
    return roomId;
  }

  /**
   * Set the room ID associated with the message.
   * @param roomId the room ID to set
   */
  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }
}
