package edu.northeastern.cs6650.chat_server.model;

public class ServerEchoResponse {
  private String status;
  private String message;
  private String serverTimestamp;


  public ServerEchoResponse(String status, String message, String serverTimestamp) {
    this.status = status;
    this.message = message;
    this.serverTimestamp = serverTimestamp;
  }

  public String getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public String getServerTimestamp() {
    return serverTimestamp;
  }
}
