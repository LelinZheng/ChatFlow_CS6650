package edu.northeastern.cs6650.chat_server.model;

/**
 * Represents a server-generated echo response sent back to clients.
 * <p>
 * This response is typically used to acknowledge receipt of a message
 * and provide server-side metadata such as processing time.
 */
public class ServerEchoResponse {
  private String status;
  private String message;
  private String serverTimestamp;


  /**
   * Constructor for ServerEchoResponse.
   * @param status status of the response
   * @param message message content
   * @param serverTimestamp timestamp from the server
   */
  public ServerEchoResponse(String status, String message, String serverTimestamp) {
    this.status = status;
    this.message = message;
    this.serverTimestamp = serverTimestamp;
  }

  /**
   * Get the status of the response.
   * @return status string
   */
  public String getStatus() {
    return status;
  }

  /**
   * Get the message content.
   * @return message string
   */
  public String getMessage() {
    return message;
  }

  /**
   * Get the server timestamp.
   * @return server timestamp string
   */
  public String getServerTimestamp() {
    return serverTimestamp;
  }
}
