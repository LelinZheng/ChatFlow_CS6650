package edu.northeastern.cs6650.consumer.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import edu.northeastern.cs6650.consumer.model.ChatMessage;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class MessageRepositoryTest {

  private JdbcTemplate mockJdbc;
  private MessageRepository repository;

  @BeforeEach
  void setUp() {
    mockJdbc = mock(JdbcTemplate.class);
    repository = new MessageRepository(mockJdbc);
  }

  private ChatMessage msg() {
    return new ChatMessage("msg-1", "1", "1", "user1", "hello",
        "2026-03-16T12:00:00+00:00", "TEXT", "server-1", "127.0.0.1");
  }

  // ── SQL ────────────────────────────────────────────────────

  @Test
  void batchInsert_callsBatchUpdateWithUpsertSql() {
    repository.batchInsert(List.of(msg()));

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(mockJdbc).batchUpdate(sqlCaptor.capture(), anyList());

    String sql = sqlCaptor.getValue();
    assertTrue(sql.contains("INSERT INTO messages"));
    assertTrue(sql.contains("ON CONFLICT (message_id) DO NOTHING"));
  }

  // ── field mapping ──────────────────────────────────────────

  @Test
  void batchInsert_mapsFieldsCorrectly() {
    ChatMessage msg = new ChatMessage(
        "msg-1", "room-5", "user-42", "alice", "hello world",
        "2026-03-16T12:00:00+00:00", "TEXT", "server-1", "10.0.0.1"
    );

    ArgumentCaptor<List<Object[]>> rowsCaptor = ArgumentCaptor.forClass(List.class);
    repository.batchInsert(List.of(msg));
    verify(mockJdbc).batchUpdate(anyString(), rowsCaptor.capture());

    Object[] row = rowsCaptor.getValue().get(0);
    assertEquals("msg-1",        row[0]); // message_id
    assertEquals("room-5",       row[1]); // room_id
    assertEquals("user-42",      row[2]); // user_id
    assertEquals("alice",        row[3]); // username
    assertEquals("hello world",  row[4]); // content ← msg.getMessage()
    assertEquals("TEXT",         row[5]); // message_type
    assertEquals("server-1",     row[6]); // server_id
    assertEquals("10.0.0.1",     row[7]); // client_ip
    assertInstanceOf(Timestamp.class, row[8]); // created_at ← parsed from ISO-8601
  }

  // ── edge cases ─────────────────────────────────────────────

  @Test
  void batchInsert_emptyList_callsBatchUpdateWithNoRows() {
    repository.batchInsert(List.of());

    ArgumentCaptor<List<Object[]>> rowsCaptor = ArgumentCaptor.forClass(List.class);
    verify(mockJdbc).batchUpdate(anyString(), rowsCaptor.capture());
    assertEquals(0, rowsCaptor.getValue().size());
  }
}
