package edu.northeastern.cs6650.consumer.db;

import edu.northeastern.cs6650.consumer.model.ChatMessage;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persists chat messages to PostgreSQL using JDBC batch inserts.
 *
 * <p>Uses an upsert strategy — duplicate message_ids are silently ignored
 * via ON CONFLICT DO NOTHING, making inserts idempotent.
 */
@Repository
public class MessageRepository {

  private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);

  private static final String UPSERT_SQL = """
      INSERT INTO messages (message_id, room_id, user_id, username, content,
          message_type, server_id, client_ip, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (message_id) DO NOTHING
      """;

  private final JdbcTemplate jdbc;

  /**
   * Constructs a MessageRepository with the given JdbcTemplate.
   *
   * @param jdbc the JdbcTemplate used to execute batch inserts
   */
  public MessageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Inserts a batch of messages into the messages table.
   * Duplicate message_ids are silently ignored.
   *
   * @param batch list of ChatMessage objects to persist
   */
  public void batchInsert(List<ChatMessage> batch) {
    List<Object[]> rows = batch.stream()
        .map(msg -> new Object[]{
            msg.getMessageId(),
            msg.getRoomId(),
            msg.getUserId(),
            msg.getUsername(),
            msg.getMessage(),           // ChatMessage.message → content column
            msg.getMessageType(),
            msg.getServerId(),
            msg.getClientIp(),
            Timestamp.from(OffsetDateTime.parse(msg.getTimestamp()).toInstant())
        })
        .toList();

    jdbc.batchUpdate(UPSERT_SQL, rows);
    log.debug("Batch inserted {} messages", batch.size());
  }
}
