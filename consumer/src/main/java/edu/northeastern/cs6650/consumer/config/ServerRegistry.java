package edu.northeastern.cs6650.consumer.config;

import edu.northeastern.cs6650.consumer.health.HealthController;
import edu.northeastern.cs6650.consumer.model.BroadcastRequest;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


/**
 * Registry of chat server instances that the consumer fans out broadcast messages to.
 * <p>
 * On startup, reads a comma-separated list of server base URLs from
 * {@code chatserver.instances} in {@code application.properties} and builds
 * an HTTP client for each. When a message is pulled from RabbitMQ,
 * {@link #broadcastToAll} posts it to every server's
 * {@code POST /internal/broadcast} endpoint. Each server then delivers
 * the message to its locally connected WebSocket sessions for that room.
 * <p>
 * Individual server failures are logged and skipped — a single unreachable
 * server does not prevent delivery to the remaining servers.
 */
@Component
public class ServerRegistry {

  private static final Logger log = LoggerFactory.getLogger(ServerRegistry.class);

  @Value("${chatserver.instances}")
  private String serverInstances;

  private List<String> servers;
  private final RestClient restClient = RestClient.create();

  /**
   * Parses the comma-separated {@code chatserver.instances} property into
   * a list of server base URLs. Called automatically by Spring after
   * {@code @Value} fields are injected.
   */
  @PostConstruct
  public void init() {
    servers = Arrays.asList(serverInstances.split(","));
    log.info("ServerRegistry initialized with {} server(s): {}", servers.size(), servers);
  }

  /**
   * Fans out a broadcast message to all registered server instances.
   * Posts {@code POST /internal/broadcast} to each server with the roomId
   * and payload. Servers that fail to respond are logged and skipped —
   * delivery continues to remaining servers.
   *
   * @param roomId  the room to broadcast to
   * @param payload the JSON string to deliver to all sessions in that room
   */
  public void broadcastToAll(String roomId, String payload) {
    BroadcastRequest request = new BroadcastRequest(roomId, payload);
    for (String server : servers) {
      try {
        restClient.post()
            .uri(server + "/internal/broadcast")
            .body(request)
            .retrieve()
            .toBodilessEntity();
      } catch (Exception e) {
        log.warn("Failed to reach server {}: {}", server, e.getMessage());
      }
    }
  }

  /**
   * Returns an unmodifiable view of the registered server URLs.
   * Used by {@link HealthController} to expose server count in the stats endpoint.
   *
   * @return list of server base URLs
   */
  public List<String> getServerInstances() {
    return Collections.unmodifiableList(servers);
  }
}
