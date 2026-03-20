package edu.northeastern.cs6650.consumer.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import edu.northeastern.cs6650.consumer.db.BatchWriter;
import edu.northeastern.cs6650.consumer.model.ChatMessage;
import edu.northeastern.cs6650.consumer.redis.RedisPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BroadcastDLQTest {

  private RedisPublisher mockRedisPublisher;
  private BatchWriter mockBatchWriter;
  private BroadcastDLQ broadcastDlq;

  @BeforeEach
  void setUp() {
    mockRedisPublisher = mock(RedisPublisher.class);
    mockBatchWriter = mock(BatchWriter.class);

    broadcastDlq = new BroadcastDLQ(mockRedisPublisher, mockBatchWriter);
    ReflectionTestUtils.setField(broadcastDlq, "retryIntervalMs", 60000L); // won't fire during tests
    ReflectionTestUtils.setField(broadcastDlq, "maxSize", 5);
    broadcastDlq.start();
  }

  @AfterEach
  void tearDown() {
    broadcastDlq.stop();
  }

  private ChatMessage msg(int i) {
    return new ChatMessage("msg-" + i, String.valueOf(i), "1", "user1", "hello",
        "2026-03-19T12:00:00+00:00", "TEXT", "server-1", "127.0.0.1");
  }

  // ── add ───────────────────────────────────────────────────

  @Test
  void add_messageQueued_sizeIncrements() {
    broadcastDlq.add(msg(1));

    assertEquals(1, broadcastDlq.size());
  }

  @Test
  void add_queueFull_doesNotThrow() {
    for (int i = 1; i <= 5; i++) broadcastDlq.add(msg(i));

    // 6th add on a full queue should not throw — just logs an error
    broadcastDlq.add(msg(6));

    assertEquals(5, broadcastDlq.size());
  }

  // ── retryAll ──────────────────────────────────────────────

  @Test
  void retryAll_emptyQueue_noInteractions() {
    broadcastDlq.retryAll();

    verify(mockRedisPublisher, never()).publish(any(), any());
    verify(mockBatchWriter, never()).enqueue(any());
  }

  @Test
  void retryAll_publishSucceeds_forwardsToWriterAndEmptiesQueue() throws Exception {
    ChatMessage msg = msg(1);
    broadcastDlq.add(msg);

    broadcastDlq.retryAll();

    verify(mockRedisPublisher).publish(eq(msg.getRoomId()), any());
    verify(mockBatchWriter).enqueue(msg);
    assertEquals(0, broadcastDlq.size());
  }

  @Test
  void retryAll_publishFails_requeuesForNextRetry() throws Exception {
    doThrow(new RuntimeException("redis down")).when(mockRedisPublisher).publish(any(), any());

    broadcastDlq.add(msg(1));
    broadcastDlq.retryAll();

    verify(mockBatchWriter, never()).enqueue(any());
    assertEquals(1, broadcastDlq.size());
  }

  @Test
  void retryAll_multipleMessages_successfulOnesRemovedFailedOnesRequeued() throws Exception {
    ChatMessage good = msg(1);
    ChatMessage bad = msg(2);

    doThrow(new RuntimeException("redis down"))
        .when(mockRedisPublisher).publish(eq(bad.getRoomId()), any());

    broadcastDlq.add(good);
    broadcastDlq.add(bad);
    broadcastDlq.retryAll();

    verify(mockBatchWriter).enqueue(good);
    verify(mockBatchWriter, never()).enqueue(bad);
    assertEquals(1, broadcastDlq.size()); // only bad message remains
  }
}
