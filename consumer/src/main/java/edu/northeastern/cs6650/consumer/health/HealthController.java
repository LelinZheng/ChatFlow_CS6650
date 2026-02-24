package edu.northeastern.cs6650.consumer.health;


import edu.northeastern.cs6650.consumer.websocket.RoomSessionHandler;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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