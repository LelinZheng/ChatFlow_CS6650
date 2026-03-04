package edu.northeastern.cs6650.chat_server.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing health check endpoints.
 * <p>
 * Used for service monitoring, readiness checks, and verifying
 * that the application is running.
 */
@RestController
public class HealthController {

  /**
   * Health check endpoint.
   * @return a map containing the health status and current timestamp and the server name
   */
  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "UP",
        "timestamp", Instant.now().toString(),
        "serverId", System.getenv().getOrDefault("SERVER_ID", "unknown")
    );
  }
}
