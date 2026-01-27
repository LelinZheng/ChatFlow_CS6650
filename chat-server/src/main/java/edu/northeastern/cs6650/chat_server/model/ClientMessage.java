package edu.northeastern.cs6650.chat_server.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientMessage {
  private String userId;
  private String username;
  private String message;
  private Messagetype type;
  private String timestamp;

  public ClientMessage(String userId, String username, String message, Messagetype type,
      String timestamp) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.type = type;
    this.timestamp = timestamp;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getMessage() {
    return message;
  }

  public Messagetype getType() {
    return type;
  }

  public String getTimestamp() {
    return timestamp;
  }
}
