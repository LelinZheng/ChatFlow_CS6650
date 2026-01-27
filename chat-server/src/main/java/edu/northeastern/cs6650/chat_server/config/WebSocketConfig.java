package edu.northeastern.cs6650.chat_server.config;

import edu.northeastern.cs6650.chat_server.websocket.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler chatHandler;

  public WebSocketConfig(ChatWebSocketHandler chatHandler) {
    this.chatHandler = chatHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(chatHandler, "/chat/{roomId}")
        .setAllowedOrigins("*");

  }
}
