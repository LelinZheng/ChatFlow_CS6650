package edu.northeastern.cs6650.consumer.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;


class RoomSessionHandlerTest {

  private RoomSessionHandler handler;

  private WebSocketSession mockSession(String id) throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    URI uri = new URI("ws://localhost:8081/chat/5");
    when(session.getUri()).thenReturn(uri);
    return session;
  }

  private WebSocketSession mockSession(String id, String roomId) throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    URI uri = new URI("ws://localhost:8081/chat/" + roomId);
    when(session.getUri()).thenReturn(uri);
    return session;
  }

  @BeforeEach
  void setUp() {
    handler = new RoomSessionHandler();
  }

  // ── connection established ─────────────────────────────────

  @Test
  void afterConnectionEstablished_addsSessionToCorrectRoom() throws Exception {
    WebSocketSession session = mockSession("s1", "5");
    handler.afterConnectionEstablished(session);
    assertEquals(1, handler.getTotalSessionCount());
    assertEquals(1, handler.getActiveRoomCount());
  }

  @Test
  void afterConnectionEstablished_multipleSessionsSameRoom_allAdded() throws Exception {
    WebSocketSession s1 = mockSession("s1", "5");
    WebSocketSession s2 = mockSession("s2", "5");
    WebSocketSession s3 = mockSession("s3", "5");

    handler.afterConnectionEstablished(s1);
    handler.afterConnectionEstablished(s2);
    handler.afterConnectionEstablished(s3);

    assertEquals(3, handler.getTotalSessionCount());
    assertEquals(1, handler.getActiveRoomCount()); // still 1 room
  }

  @Test
  void afterConnectionEstablished_differentRooms_trackedSeparately() throws Exception {
    handler.afterConnectionEstablished(mockSession("s1", "1"));
    handler.afterConnectionEstablished(mockSession("s2", "2"));
    handler.afterConnectionEstablished(mockSession("s3", "3"));

    assertEquals(3, handler.getTotalSessionCount());
    assertEquals(3, handler.getActiveRoomCount());
  }

  // ── connection closed ──────────────────────────────────────

  @Test
  void afterConnectionClosed_removesSessionFromRoom() throws Exception {
    WebSocketSession session = mockSession("s1", "5");
    handler.afterConnectionEstablished(session);
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    assertEquals(0, handler.getTotalSessionCount());
    assertEquals(0, handler.getActiveRoomCount());
  }

  @Test
  void afterConnectionClosed_onlyRemovesCorrectSession() throws Exception {
    WebSocketSession s1 = mockSession("s1", "5");
    WebSocketSession s2 = mockSession("s2", "5");

    handler.afterConnectionEstablished(s1);
    handler.afterConnectionEstablished(s2);
    handler.afterConnectionClosed(s1, CloseStatus.NORMAL);

    assertEquals(1, handler.getTotalSessionCount());
  }

  @Test
  void afterConnectionClosed_unknownSession_doesNotThrow() throws Exception {
    WebSocketSession unknown = mockSession("unknown", "5");
    assertDoesNotThrow(() ->
        handler.afterConnectionClosed(unknown, CloseStatus.NORMAL));
  }

  // ── broadcast ─────────────────────────────────────────────

  @Test
  void broadcastToRoom_sendsToAllSessionsInRoom() throws Exception {
    WebSocketSession s1 = mockSession("s1", "5");
    WebSocketSession s2 = mockSession("s2", "5");

    handler.afterConnectionEstablished(s1);
    handler.afterConnectionEstablished(s2);
    handler.broadcastToRoom("5", "hello");

    verify(s1).sendMessage(new TextMessage("hello"));
    verify(s2).sendMessage(new TextMessage("hello"));
  }

  @Test
  void broadcastToRoom_doesNotSendToOtherRooms() throws Exception {
    WebSocketSession s1 = mockSession("s1", "5");
    WebSocketSession s2 = mockSession("s2", "7");

    handler.afterConnectionEstablished(s1);
    handler.afterConnectionEstablished(s2);
    handler.broadcastToRoom("5", "hello");

    verify(s1).sendMessage(new TextMessage("hello"));
    verify(s2, never()).sendMessage(any());
  }

  @Test
  void broadcastToRoom_emptyRoom_doesNotThrow() {
    assertDoesNotThrow(() -> handler.broadcastToRoom("99", "hello"));
  }

  @Test
  void broadcastToRoom_removesDeadSessions() throws Exception {
    WebSocketSession dead = mockSession("s1", "5");
    when(dead.isOpen()).thenReturn(false); // simulate dead session

    handler.afterConnectionEstablished(dead);
    handler.broadcastToRoom("5", "hello");

    assertEquals(0, handler.getTotalSessionCount());
    verify(dead, never()).sendMessage(any());
  }

  @Test
  void broadcastToRoom_sessionThrowsOnSend_sessionRemovedAndOthersStillReceive()
      throws Exception {
    WebSocketSession bad = mockSession("s1", "5");
    WebSocketSession good = mockSession("s2", "5");
    doThrow(new IOException("send failed")).when(bad).sendMessage(any());

    handler.afterConnectionEstablished(bad);
    handler.afterConnectionEstablished(good);
    handler.broadcastToRoom("5", "hello");

    verify(good).sendMessage(new TextMessage("hello"));
    assertEquals(1, handler.getTotalSessionCount());
  }
}
