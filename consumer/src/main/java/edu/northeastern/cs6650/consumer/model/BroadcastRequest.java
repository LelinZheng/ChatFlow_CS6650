package edu.northeastern.cs6650.consumer.model;


/**
 * Request body for the internal broadcast endpoint.
 * Carries the target room and the JSON payload to broadcast
 * to all connected clients in that room.
 */
public class BroadcastRequest {

  private String roomId;
  private String payload;

  /**
   * Default constructor for BroadcastRequest. Required for JSON deserialization.
   */
  public BroadcastRequest() {
  }

  /**
   * Constructor for BroadcastRequest.
   * @param roomId the target room to broadcast to
   * @param payload the JSON string to send to clients in that room
   */
  public BroadcastRequest(String roomId, String payload) {
    this.roomId = roomId;
    this.payload = payload;
  }

  /**
   * Gets the target room ID for this broadcast request.
   * @return the target room to broadcast to
   */
  public String getRoomId() {
    return roomId;
  }

  /**
   * Sets the target room ID for this broadcast request.
   * @param roomId the target room to broadcast to
   */
  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  /**
   * Gets the JSON payload to broadcast to clients in the target room.
   * @return the JSON string to send to clients in the target room
   */
  public String getPayload() {
    return payload;
  }

  /**
   * Sets the JSON payload to broadcast to clients in the target room.
   * @param payload the JSON string to send to clients in the target room
   */
  public void setPayload(String payload) {
    this.payload = payload;
  }
}

