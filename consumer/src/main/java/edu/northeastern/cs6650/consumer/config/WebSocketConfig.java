package edu.northeastern.cs6650.consumer.config;

import edu.northeastern.cs6650.consumer.websocket.RoomSessionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration class for registering WebSocket handlers.
 * <p>
 * Enables WebSocket support and maps WebSocket endpoints
 * to their corresponding handlers.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final RoomSessionHandler roomSessionHandler;

  /**
   * Constructor to initialize WebSocketConfig with ChatWebSocketHandler.
   * @param roomSessionHandler the room session WebSocket handler
   */
  public WebSocketConfig(RoomSessionHandler roomSessionHandler) {
    this.roomSessionHandler = roomSessionHandler;
  }

  /**
   * Register WebSocket handlers.
   * @param registry the WebSocketHandlerRegistry
   */
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(roomSessionHandler, "/chat/{roomId}")
        .setAllowedOrigins("*");

  }
}
