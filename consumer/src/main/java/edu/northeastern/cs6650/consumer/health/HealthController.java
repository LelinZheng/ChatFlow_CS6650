package edu.northeastern.cs6650.consumer.health;

import edu.northeastern.cs6650.consumer.websocket.RoomSessionHandler;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing health check and runtime metrics endpoints.
 * <p>
 * {@code GET /health} is used by the AWS ALB to verify the consumer is alive.
 * {@code GET /health/stats} is called by monitoring scripts during load tests
 * to collect active room and session counts alongside RabbitMQ queue metrics.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  private final RoomSessionHandler roomSessionHandler;

  public HealthController(RoomSessionHandler roomSessionHandler) {
    this.roomSessionHandler = roomSessionHandler;
  }

  /**
   * Basic health check — ALB calls this every 30 seconds.
   * Returns 200 OK as long as the consumer is running.
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("timestamp", Instant.now().toString());
    return ResponseEntity.ok(response);
  }

  /**
   * Detailed stats — called by your monitoring script during load tests.
   */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> stats() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("timestamp", Instant.now().toString());
    response.put("activeRooms", roomSessionHandler.getActiveRoomCount());
    response.put("totalSessions", roomSessionHandler.getTotalSessionCount());
    return ResponseEntity.ok(response);
  }
}