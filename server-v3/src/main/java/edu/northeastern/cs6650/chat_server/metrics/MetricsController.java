package edu.northeastern.cs6650.chat_server.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that returns core query results and analytics over all stored messages.
 * <p>
 * Intended to be called once after a load test completes. All parameters are optional —
 * if omitted, the endpoint auto-selects the most active room/user and the full time range
 * as sample inputs for the core queries.
 *
 * <pre>GET /api/metrics?roomId=X&userId=Y&startTime=Z&endTime=W&topN=10</pre>
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

  private final MetricsRepository repo;

  /**
   * Constructs a MetricsController with the given MetricsRepository.
   * @param repo the repository used to execute all queries
   */
  public MetricsController(MetricsRepository repo) {
    this.repo = repo;
  }

  /**
   * Returns core query results and analytics for all stored messages.
   * <p>
   * Core query parameters default to the most active room/user and the full time range
   * when not explicitly provided.
   *
   * @param roomId    optional roomId for the room time-range query
   * @param userId    optional userId for the user history and user-rooms queries
   * @param startTime optional ISO-8601 start time for time-windowed queries
   * @param endTime   optional ISO-8601 end time for time-windowed queries
   * @param topN      number of results for top-N analytics queries (default 10)
   * @return JSON map containing totalMessages, coreQueries, and analytics sections
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> getMetrics(
      @RequestParam(required = false) String roomId,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime,
      @RequestParam(defaultValue = "10") int topN) {

    // Resolve defaults from data if params not supplied
    String resolvedRoomId    = roomId    != null ? roomId    : repo.mostActiveRoomId();
    String resolvedUserId    = userId    != null ? userId    : repo.mostActiveUserId();
    String resolvedStartTime = startTime != null ? startTime : repo.minTimestamp();
    String resolvedEndTime   = endTime   != null ? endTime   : repo.maxTimestamp();

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("totalMessages", repo.totalMessageCount());

    // Echo back the inputs used for core queries so the client can log them
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("roomId", resolvedRoomId);
    inputs.put("userId", resolvedUserId);
    inputs.put("startTime", resolvedStartTime);
    inputs.put("endTime", resolvedEndTime);
    response.put("coreQueryInputs", inputs);

    // Core queries
    Map<String, Object> core = new LinkedHashMap<>();
    core.put("roomMessagesInTimeRange",
        repo.roomMessagesInTimeRange(resolvedRoomId, resolvedStartTime, resolvedEndTime));
    core.put("userMessageHistory",
        repo.userMessageHistory(resolvedUserId, resolvedStartTime, resolvedEndTime));
    core.put("activeUserCount",
        repo.activeUserCount(resolvedStartTime, resolvedEndTime));
    core.put("userRoomsParticipated",
        repo.userRoomsParticipated(resolvedUserId));
    response.put("coreQueries", core);

    // Analytics
    Map<String, Object> analytics = new LinkedHashMap<>();
    analytics.put("messagesPerMinute",    repo.messagesPerMinute());
    analytics.put("mostActiveUsers",      repo.mostActiveUsers(topN));
    analytics.put("mostActiveRooms",      repo.mostActiveRooms(topN));
    analytics.put("userParticipationPatterns", repo.userParticipationPatterns(topN));
    response.put("analytics", analytics);

    return ResponseEntity.ok(response);
  }
}
