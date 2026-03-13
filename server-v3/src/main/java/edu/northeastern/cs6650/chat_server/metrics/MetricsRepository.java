package edu.northeastern.cs6650.chat_server.metrics;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Executes all core and analytics queries against the messages table.
 * <p>
 * Core query methods accept explicit parameters (roomId, userId, startTime, endTime)
 * so they can be called with real inputs or with auto-selected defaults from the data.
 */
@Repository
public class MetricsRepository {

  private final JdbcTemplate jdbc;

  /**
   * Constructs a MetricsRepository with the given JdbcTemplate.
   * @param jdbc the JdbcTemplate used to execute queries
   */
  public MetricsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ----------------------------------------------------------------
  // Core Queries
  // ----------------------------------------------------------------

  /**
   * Returns up to 1000 messages for the given room in the given time range, ordered by time.
   * @param roomId    the room to query
   * @param startTime ISO-8601 timestamp string for the start of the range (inclusive)
   * @param endTime   ISO-8601 timestamp string for the end of the range (inclusive)
   * @return ordered list of message rows
   */
  public List<Map<String, Object>> roomMessagesInTimeRange(
      String roomId, String startTime, String endTime) {
    return jdbc.queryForList("""
        SELECT message_id, room_id, user_id, username,
               content, message_type, created_at
        FROM messages
        WHERE room_id = ?
          AND created_at BETWEEN ?::timestamptz AND ?::timestamptz
        ORDER BY created_at ASC
        LIMIT 1000
        """, roomId, startTime, endTime);
  }

  /**
   * Returns message history for the given user across all rooms, ordered by most recent first.
   * @param userId    the user to query
   * @param startTime ISO-8601 timestamp string for the start of the range, or null for no lower bound
   * @param endTime   ISO-8601 timestamp string for the end of the range, or null for no upper bound
   * @return list of message rows capped at 500
   */
  public List<Map<String, Object>> userMessageHistory(
      String userId, String startTime, String endTime) {
    return jdbc.queryForList("""
        SELECT message_id, room_id, user_id, username,
               content, message_type, created_at
        FROM messages
        WHERE user_id = ?
          AND (? IS NULL OR created_at >= ?::timestamptz)
          AND (? IS NULL OR created_at <= ?::timestamptz)
        ORDER BY created_at DESC
        LIMIT 500
        """, userId, startTime, startTime, endTime, endTime);
  }

  /**
   * Returns the count of unique users who sent a message within the given time window.
   * @param startTime ISO-8601 timestamp string for the start of the window (inclusive)
   * @param endTime   ISO-8601 timestamp string for the end of the window (inclusive)
   * @return unique user count
   */
  public long activeUserCount(String startTime, String endTime) {
    Long count = jdbc.queryForObject("""
        SELECT COUNT(DISTINCT user_id)
        FROM messages
        WHERE created_at BETWEEN ?::timestamptz AND ?::timestamptz
        """, Long.class, startTime, endTime);
    return count != null ? count : 0L;
  }

  /**
   * Returns the list of rooms the given user has participated in, with last activity timestamp.
   * @param userId the user to query
   * @return list of rows containing roomId and last_activity, ordered by most recent
   */
  public List<Map<String, Object>> userRoomsParticipated(String userId) {
    return jdbc.queryForList("""
        SELECT room_id, MAX(created_at) AS last_activity
        FROM messages
        WHERE user_id = ?
        GROUP BY room_id
        ORDER BY last_activity DESC
        """, userId);
  }

  // ----------------------------------------------------------------
  // Default value helpers (used when caller does not supply parameters)
  // ----------------------------------------------------------------

  /**
   * Returns the room_id of the room with the highest message count.
   * @return most active roomId string
   */
  public String mostActiveRoomId() {
    return jdbc.queryForObject("""
        SELECT room_id FROM messages
        GROUP BY room_id
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """, String.class);
  }

  /**
   * Returns the user_id of the user with the highest message count.
   * @return most active userId string
   */
  public String mostActiveUserId() {
    return jdbc.queryForObject("""
        SELECT user_id FROM messages
        GROUP BY user_id
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """, String.class);
  }

  /**
   * Returns the earliest created_at timestamp in the messages table as an ISO-8601 string.
   * @return min timestamp string
   */
  public String minTimestamp() {
    return jdbc.queryForObject(
        "SELECT MIN(created_at)::text FROM messages", String.class);
  }

  /**
   * Returns the latest created_at timestamp in the messages table as an ISO-8601 string.
   * @return max timestamp string
   */
  public String maxTimestamp() {
    return jdbc.queryForObject(
        "SELECT MAX(created_at)::text FROM messages", String.class);
  }

  // ----------------------------------------------------------------
  // Analytics Queries
  // ----------------------------------------------------------------

  /**
   * Returns message counts bucketed by minute across the full dataset.
   * @return list of rows with bucket (minute) and message_count
   */
  public List<Map<String, Object>> messagesPerMinute() {
    return jdbc.queryForList("""
        SELECT DATE_TRUNC('minute', created_at) AS bucket,
               COUNT(*) AS message_count
        FROM messages
        GROUP BY bucket
        ORDER BY bucket
        """);
  }

  /**
   * Returns the top N users by message count.
   * @param topN number of results to return
   * @return list of rows with user_id, username, and message_count
   */
  public List<Map<String, Object>> mostActiveUsers(int topN) {
    return jdbc.queryForList("""
        SELECT user_id, username, COUNT(*) AS message_count
        FROM messages
        GROUP BY user_id, username
        ORDER BY message_count DESC
        LIMIT ?
        """, topN);
  }

  /**
   * Returns the top N rooms by message count.
   * @param topN number of results to return
   * @return list of rows with room_id and message_count
   */
  public List<Map<String, Object>> mostActiveRooms(int topN) {
    return jdbc.queryForList("""
        SELECT room_id, COUNT(*) AS message_count
        FROM messages
        GROUP BY room_id
        ORDER BY message_count DESC
        LIMIT ?
        """, topN);
  }

  /**
   * Returns top N users ranked by number of distinct rooms participated in,
   * broken down by message type (TEXT, JOIN, LEAVE counts).
   * @param topN number of results to return
   * @return list of rows with user_id, username, rooms_participated, text_count,
   *         join_count, and leave_count
   */
  public List<Map<String, Object>> userParticipationPatterns(int topN) {
    return jdbc.queryForList("""
        SELECT user_id,
               username,
               COUNT(DISTINCT room_id)                              AS rooms_participated,
               COUNT(*) FILTER (WHERE message_type = 'TEXT')       AS text_count,
               COUNT(*) FILTER (WHERE message_type = 'JOIN')       AS join_count,
               COUNT(*) FILTER (WHERE message_type = 'LEAVE')      AS leave_count
        FROM messages
        GROUP BY user_id, username
        ORDER BY rooms_participated DESC
        LIMIT ?
        """, topN);
  }

  /**
   * Returns the total number of messages stored in the table.
   * @return total message count
   */
  public long totalMessageCount() {
    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class);
    return count != null ? count : 0L;
  }
}
