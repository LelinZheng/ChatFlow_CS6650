package edu.northeastern.cs6650.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ChatMessage {
  private String messageId;
  private String userId;
  private String username;
  private String message;
  private MessageType messageType;
  private String timestamp;
  @JsonIgnore
  private int roomId;

  public ChatMessage() {
  }

  public ChatMessage(String userId, String username, String message, MessageType messageType,
      String timestamp, int roomId) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.messageType = messageType;
    this.timestamp = timestamp;
    this.roomId = roomId;
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

  public MessageType getMessageType() {
    return messageType;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public int getRoomId() {
    return roomId;
  }

  public void setRoomId(int roomId) {
    this.roomId = roomId;
  }
}
