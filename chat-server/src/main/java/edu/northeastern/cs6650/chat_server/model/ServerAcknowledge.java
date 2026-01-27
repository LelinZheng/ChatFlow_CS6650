package edu.northeastern.cs6650.chat_server.model;

public class ServerAcknowledge {
  private String status;
  private String serverTimestamp;
  private String roomId;

  public ServerAcknowledge(String status, String serverTimestamp, String roomId) {
    this.status = status;
    this.serverTimestamp = serverTimestamp;
    this.roomId = roomId;
  }

  public String getStatus() {
    return status;
  }

  public String getServerTimestamp() {
    return serverTimestamp;
  }

  public String getRoomId() {
    return roomId;
  }
}
