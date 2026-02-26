package edu.northeastern.cs6650.chat_server.controller;

import edu.northeastern.cs6650.chat_server.model.BroadcastRequest;
import edu.northeastern.cs6650.chat_server.websocket.RoomManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST endpoint called by the consumer application to broadcast
 * a message to all WebSocket clients connected to a specific room on this server.
 * <p>
 * This endpoint is not exposed to external clients — it is only called by the
 * consumer after pulling a message from RabbitMQ. Each server instance receives
 * the same broadcast request and delivers it to whichever sessions it holds locally.
 */
@RestController
public class InternalBroadcastController {

  private final RoomManager roomManager;

  /**
   * Constructor for InternalBroadcastController.
   * @param roomManager manages WebSocket sessions for broadcasting messages to clients
   */
  public InternalBroadcastController(RoomManager roomManager) {
    this.roomManager = roomManager;
  }

  /**
   * Broadcasts a message to all active WebSocket sessions in the specified room.
   * Delegates to {@link RoomManager#broadcastToRoom} which handles dead session
   * cleanup and individual send failures.
   *
   * @param request contains the roomId and JSON payload to broadcast
   * @return 200 OK always — individual session failures are handled internally
   */
  @PostMapping("/internal/broadcast")
  public ResponseEntity<Void> broadcast(@RequestBody BroadcastRequest request) {
    roomManager.broadcastToRoom(
        request.getRoomId(),
        request.getPayload()
    );
    return ResponseEntity.ok().build();
  }

}
