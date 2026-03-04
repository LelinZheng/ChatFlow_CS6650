package edu.northeastern.cs6650.chat_server.websocket;

import edu.northeastern.cs6650.chat_server.controller.InternalBroadcastController;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Manages WebSocket session state for all chat rooms on this server instance.
 * <p>
 * Maintains a bidirectional index:
 * <ul>
 *   <li>{@code roomSessions}: roomId → all active sessions in that room (for broadcasting)</li>
 *   <li>{@code sessionRoomMap}: sessionId → roomId (for fast cleanup on disconnect)</li>
 * </ul>
 * Called by {@link ChatWebSocketHandler} on connect/disconnect,
 * and by {@link InternalBroadcastController} to broadcast messages from the consumer.
 */
@Component
public class RoomManager {

  private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

  // roomId -> all active sessions in that room
  private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions =
      new ConcurrentHashMap<>();

  // sessionId -> roomId
  private final ConcurrentHashMap<String, String> sessionRoomMap =
      new ConcurrentHashMap<>();

  /**
   * Adds a session to the specified room.
   * Called by {@link ChatWebSocketHandler#afterConnectionEstablished}.
   *
   * @param roomId  the room the client connected to
   * @param session the client's WebSocket session
   */
  public void addSession(String roomId, WebSocketSession session) {
    // If the session is already in a different room, remove it first
    String currentRoom = sessionRoomMap.get(session.getId());
    if (currentRoom != null && !currentRoom.equals(roomId)) {
      Set<WebSocketSession> currentSessions = roomSessions.get(currentRoom);
      if (currentSessions != null) {
        currentSessions.remove(session);
        log.info("Session {} auto-left room {} to join room {}", session.getId(), currentRoom, roomId);
      }
    }

    roomSessions
        .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
        .add(session);
    sessionRoomMap.put(session.getId(), roomId);
    log.info("Session {} joined room {} — room now has {} sessions",
        session.getId(), roomId, roomSessions.get(roomId).size());
  }

  /**
   * Removes a session from its room.
   * Called by {@link ChatWebSocketHandler#afterConnectionClosed}.
   *
   * @param session the client's WebSocket session that disconnected
   */
  public void removeSession(WebSocketSession session) {
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
   * Dead sessions are removed silently during broadcast.
   * Called by {@link InternalBroadcastController} when the consumer
   * delivers a message for this room.
   *
   * @param roomId  the room to broadcast to
   * @param payload the JSON string to send to all sessions
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
