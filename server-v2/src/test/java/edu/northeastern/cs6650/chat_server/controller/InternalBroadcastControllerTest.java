package edu.northeastern.cs6650.chat_server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import edu.northeastern.cs6650.chat_server.model.BroadcastRequest;
import edu.northeastern.cs6650.chat_server.websocket.RoomManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

class InternalBroadcastControllerTest {

  private RoomManager roomManager;
  private InternalBroadcastController controller;

  @BeforeEach
  void setUp() {
    roomManager = mock(RoomManager.class);
    controller = new InternalBroadcastController(roomManager);
  }

  // ── endpoint wiring ────────────────────────────────────────

  @Test
  void broadcast_validRequest_returns200() {
    BroadcastRequest request = new BroadcastRequest("room1", "{\"msg\":\"hello\"}");
    ResponseEntity<Void> response = controller.broadcast(request);

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void broadcast_validRequest_delegatesToRoomManagerWithCorrectArgs() {
    BroadcastRequest request = new BroadcastRequest("room5", "some-payload");
    controller.broadcast(request);

    ArgumentCaptor<String> roomCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(roomManager).broadcastToRoom(roomCaptor.capture(), payloadCaptor.capture());
    assertEquals("room5", roomCaptor.getValue());
    assertEquals("some-payload", payloadCaptor.getValue());
  }

  @Test
  void broadcast_nullRoomId_stillReturns200AndPassesNullThrough() {
    BroadcastRequest request = new BroadcastRequest(null, "some-payload");
    ResponseEntity<Void> response = controller.broadcast(request);

    verify(roomManager).broadcastToRoom(null, "some-payload");
    assertEquals(200, response.getStatusCode().value());
  }
}