package edu.northeastern.cs6650.chat_server.config;

import edu.northeastern.cs6650.chat_server.websocket.ChatWebSocketHandler;
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

  private final ChatWebSocketHandler chatHandler;

  /**
   * Constructor to initialize WebSocketConfig with ChatWebSocketHandler.
   * @param chatHandler the chat WebSocket handler
   */
  public WebSocketConfig(ChatWebSocketHandler chatHandler) {
    this.chatHandler = chatHandler;
  }

  /**
   * Register WebSocket handlers.
   * @param registry the WebSocketHandlerRegistry
   */
  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(chatHandler, "/chat/{roomId}")
        .setAllowedOrigins("*");

  }
}
