package edu.northeastern.cs6650.chat_server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a message sent by a client over WebSocket.
 * <p>
 * This object is used to deserialize incoming WebSocket messages
 * and encapsulates metadata such as sender identity, message type,
 * and timestamp.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientMessage {
  private String userId;
  private String username;
  private String message;
  private Messagetype messageType;
  private String timestamp;

  /**
   * Default constructor.
   */
  public ClientMessage() {
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Messagetype getMessageType() {
    return messageType;
  }

  public void setMessageType(Messagetype messageType) {
    this.messageType = messageType;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }
}
