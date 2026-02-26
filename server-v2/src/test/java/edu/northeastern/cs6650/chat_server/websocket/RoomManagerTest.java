package edu.northeastern.cs6650.chat_server.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class RoomManagerTest {

  private RoomManager roomManager;

  @BeforeEach
  void setUp() {
    roomManager = new RoomManager();
  }

  private WebSocketSession openSession() {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(UUID.randomUUID().toString());
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private WebSocketSession closedSession() {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(UUID.randomUUID().toString());
    when(session.isOpen()).thenReturn(false);
    return session;
  }

  // ── addSession ─────────────────────────────────────────────

  @Test
  void addSession_singleSession_trackedCorrectly() {
    roomManager.addSession("room1", openSession());

    assertEquals(1, roomManager.getTotalSessionCount());
    assertEquals(1, roomManager.getActiveRoomCount());
  }

  @Test
  void addSession_multipleSessionsSameRoom_allTracked() {
    roomManager.addSession("room1", openSession());
    roomManager.addSession("room1", openSession());

    assertEquals(2, roomManager.getTotalSessionCount());
    assertEquals(1, roomManager.getActiveRoomCount()); // still one room
  }

  @Test
  void addSession_differentRooms_bothRoomsActive() {
    roomManager.addSession("room1", openSession());
    roomManager.addSession("room2", openSession());

    assertEquals(2, roomManager.getTotalSessionCount());
    assertEquals(2, roomManager.getActiveRoomCount());
  }

  // ── removeSession ──────────────────────────────────────────

  @Test
  void removeSession_removesSessionFromTracking() {
    WebSocketSession session = openSession();
    roomManager.addSession("room1", session);

    roomManager.removeSession(session);

    assertEquals(0, roomManager.getTotalSessionCount());
  }

  @Test
  void removeSession_lastSessionInRoom_activeRoomCountDrops() {
    WebSocketSession session = openSession();
    roomManager.addSession("room1", session);

    roomManager.removeSession(session);

    assertEquals(0, roomManager.getActiveRoomCount());
  }

  @Test
  void removeSession_sessionNotInAnyRoom_noException() {
    assertDoesNotThrow(() -> roomManager.removeSession(openSession()));
  }

  @Test
  void removeSession_onlyRemovesTargetSession_othersUnaffected() {
    WebSocketSession s1 = openSession();
    WebSocketSession s2 = openSession();
    roomManager.addSession("room1", s1);
    roomManager.addSession("room1", s2);

    roomManager.removeSession(s1);

    assertEquals(1, roomManager.getTotalSessionCount());
    assertEquals(1, roomManager.getActiveRoomCount());
  }

  // ── broadcastToRoom ────────────────────────────────────────

  @Test
  void broadcastToRoom_sendsToAllOpenSessions() throws Exception {
    WebSocketSession s1 = openSession();
    WebSocketSession s2 = openSession();
    roomManager.addSession("room1", s1);
    roomManager.addSession("room1", s2);

    roomManager.broadcastToRoom("room1", "{\"msg\":\"hello\"}");

    verify(s1).sendMessage(any(TextMessage.class));
    verify(s2).sendMessage(any(TextMessage.class));
  }

  @Test
  void broadcastToRoom_unknownRoom_doesNothing() {
    assertDoesNotThrow(() -> roomManager.broadcastToRoom("nonexistent", "payload"));
  }

  @Test
  void broadcastToRoom_roomWithNoSessions_doesNothing() {
    // Add then remove the only session so the room entry exists but is empty
    WebSocketSession session = openSession();
    roomManager.addSession("room1", session);
    roomManager.removeSession(session);

    assertDoesNotThrow(() -> roomManager.broadcastToRoom("room1", "payload"));
  }

  @Test
  void broadcastToRoom_removesDeadSessions_andDoesNotSendToThem() throws Exception {
    WebSocketSession dead = closedSession();
    roomManager.addSession("room1", dead);

    roomManager.broadcastToRoom("room1", "payload");

    verify(dead, never()).sendMessage(any());
    assertEquals(0, roomManager.getTotalSessionCount());
  }

  @Test
  void broadcastToRoom_removesSessionsThatThrowOnSend() throws Exception {
    WebSocketSession bad = openSession();
    doThrow(new RuntimeException("send failed")).when(bad).sendMessage(any());
    roomManager.addSession("room1", bad);

    assertDoesNotThrow(() -> roomManager.broadcastToRoom("room1", "payload"));
    assertEquals(0, roomManager.getTotalSessionCount());
  }

  @Test
  void broadcastToRoom_mixedSessions_sendsOnlyToOpenAndRemovesDead() throws Exception {
    WebSocketSession open = openSession();
    WebSocketSession dead = closedSession();
    roomManager.addSession("room1", open);
    roomManager.addSession("room1", dead);

    roomManager.broadcastToRoom("room1", "payload");

    verify(open).sendMessage(any(TextMessage.class));
    verify(dead, never()).sendMessage(any());
    assertEquals(1, roomManager.getTotalSessionCount()); // only the open session remains
  }

  // ── counts ─────────────────────────────────────────────────

  @Test
  void getActiveRoomCount_emptyManager_returnsZero() {
    assertEquals(0, roomManager.getActiveRoomCount());
  }

  @Test
  void getTotalSessionCount_emptyManager_returnsZero() {
    assertEquals(0, roomManager.getTotalSessionCount());
  }
}
