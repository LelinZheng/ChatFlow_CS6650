package edu.northeastern.cs6650.consumer.websocket;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler that manages client connections on the consumer side.
 * <p>
 * Maintains a map of roomId -> active WebSocket sessions.
 * When a client connects to /chat/{roomId}, their session is added to that room.
 * When they disconnect, their session is removed.
 * RoomConsumer calls broadcastToRoom() to send messages to all sessions in a room.
 */
@Component
public class RoomSessionHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(RoomSessionHandler.class);

  // roomId -> all active sessions in that room
  private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions =
      new ConcurrentHashMap<>();

  // sessionId -> roomId
  private final ConcurrentHashMap<String, String> sessionRoomMap =
      new ConcurrentHashMap<>();

  /**
   * Called when a client connects to /chat/{roomId}.
   * Extracts roomId from the URI and adds session to that room.
   */
  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String roomId = extractRoomId(session);
    if (roomId == null) {
      log.warn("Could not extract roomId from session {}, closing", session.getId());
      try { session.close(); } catch (Exception ignored) {}
      return;
    }

    roomSessions
        .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
        .add(session);
    sessionRoomMap.put(session.getId(), roomId);

    log.info("Session {} joined room {} — room now has {} sessions",
        session.getId(), roomId, roomSessions.get(roomId).size());
  }

  /**
   * Called when a client disconnects.
   * Removes session from its room.
   */
  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = sessionRoomMap.remove(session.getId());
    if (roomId != null) {
      Set<WebSocketSession> sessions = roomSessions.get(roomId);
      if (sessions != null) {
        sessions.remove(session);
        log.info("Session {} left room {} — room now has {} sessions",
            session.getId(), roomId, sessions.size());
      }
    }
  }

  /**
   * Broadcasts a message to all active sessions in a room.
   * Removes dead sessions silently.
   * Individual WebSocket exceptions are logged but do not affect other sessions.
   * Called by RoomConsumer after pulling a message from RabbitMQ.
   *
   * @param roomId  the room to broadcast to
   * @param payload the JSON string to send
   */
  public void broadcastToRoom(String roomId, String payload) {
    Set<WebSocketSession> sessions = roomSessions.get(roomId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }

    TextMessage message = new TextMessage(payload);
    List<WebSocketSession> dead = new ArrayList<>();

    for (WebSocketSession session : sessions) {
      if (!session.isOpen()) {
        dead.add(session);
        continue;
      }
      try {
        session.sendMessage(message);
      } catch (Exception e) {
        log.warn("Failed to send to session {} in room {}: {}",
            session.getId(), roomId, e.getMessage());
        dead.add(session);
      }
    }

    // Clean up dead sessions
    if (!dead.isEmpty()) {
      sessions.removeAll(dead);
      dead.forEach(s -> sessionRoomMap.remove(s.getId()));
      log.info("Removed {} dead sessions from room {}", dead.size(), roomId);
    }
  }

  /**
   * Handles incoming messages from clients.
   * In this consumer, we do not expect any messages from clients,
   * so we simply log and ignore them. All communication is one-way from server to client.
   * @param session the WebSocket session that sent the message
   * @param message the text message received from the client
   */
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    log.debug("Consumer received unexpected message from session {}, ignoring",
        session.getId());
  }

  /**
   * Extracts the roomId from the WebSocket session URI.
   * @param session the WebSocket session to extract from
   * @return the roomId string, or null if it cannot be extracted
   */
  private String extractRoomId(WebSocketSession session) {
    try {
      String path = session.getUri().getPath(); // e.g. /chat/5
      return path.substring(path.lastIndexOf('/') + 1);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Returns the number of rooms that currently have at least one active session.
   * @return count of active rooms
   */
  public int getActiveRoomCount() {
    return (int) roomSessions.entrySet().stream()
        .filter(e -> !e.getValue().isEmpty())
        .count();
  }

  /**
   * Returns the total number of active WebSocket sessions across all rooms.
   * @return total count of active sessions
   */
  public int getTotalSessionCount() {
    return roomSessions.values().stream()
        .mapToInt(Set::size)
        .sum();
  }
}
