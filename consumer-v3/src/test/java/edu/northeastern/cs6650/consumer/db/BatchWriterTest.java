package edu.northeastern.cs6650.consumer.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs6650.consumer.circuitbreaker.DbCircuitBreaker;
import edu.northeastern.cs6650.consumer.metrics.BatchMetrics;
import edu.northeastern.cs6650.consumer.model.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BatchWriterTest {

  private MessageRepository mockRepository;
  private DbCircuitBreaker mockCircuitBreaker;
  private DeadLetterQueue mockDlq;
  private BatchMetrics mockMetrics;
  private BatchWriter batchWriter;

  @BeforeEach
  void setUp() {
    mockRepository = mock(MessageRepository.class);
    mockCircuitBreaker = mock(DbCircuitBreaker.class);
    mockDlq = mock(DeadLetterQueue.class);
    mockMetrics = mock(BatchMetrics.class);

    batchWriter = new BatchWriter(mockRepository, mockCircuitBreaker, mockDlq, mockMetrics);
    ReflectionTestUtils.setField(batchWriter, "batchSize", 3);
    ReflectionTestUtils.setField(batchWriter, "flushIntervalMs", 60000L); // won't fire during tests
    ReflectionTestUtils.setField(batchWriter, "writerThreadCount", 1);
    ReflectionTestUtils.setField(batchWriter, "bufferMaxSize", 5);
    ReflectionTestUtils.setField(batchWriter, "maxRetries", 3);
    batchWriter.start();
  }

  @AfterEach
  void tearDown() {
    batchWriter.stop();
  }

  private ChatMessage msg(int i) {
    return new ChatMessage("msg-" + i, "1", "1", "user1", "hello",
        "2026-03-16T12:00:00+00:00", "TEXT", "server-1", "127.0.0.1");
  }

  // ── enqueue ────────────────────────────────────────────────

  @Test
  void enqueue_bufferNotFull_messageBuffered() {
    batchWriter.enqueue(msg(1));

    assertEquals(1, batchWriter.bufferSize());
    verify(mockDlq, never()).add(any());
    verify(mockMetrics, never()).addDropped(anyLong());
  }

  @Test
  void enqueue_bufferFull_goesToDlq() {
    // bufferMaxSize=5, enqueue 6 — 6th must overflow
    for (int i = 1; i <= 6; i++) batchWriter.enqueue(msg(i));

    verify(mockDlq, atLeastOnce()).add(any());
    verify(mockMetrics, atLeastOnce()).addDropped(1);
  }

  @Test
  void enqueue_reachesBatchSize_triggersImmediateFlush() {
    when(mockCircuitBreaker.allowRequest()).thenReturn(true);

    // batchSize=3, enqueuing 3rd message submits an immediate drainAndWrite task
    batchWriter.enqueue(msg(1));
    batchWriter.enqueue(msg(2));
    batchWriter.enqueue(msg(3));

    // scheduler runs drainAndWrite asynchronously — wait up to 2s
    verify(mockRepository, timeout(2000)).batchInsert(anyList());
  }

  // ── drainAndWrite ──────────────────────────────────────────

  @Test
  void drainAndWrite_emptyBuffer_doesNothing() {
    batchWriter.drainAndWrite();

    verify(mockRepository, never()).batchInsert(any());
    verify(mockDlq, never()).add(any());
  }

  @Test
  void drainAndWrite_circuitBreakerOpen_batchGoesToDlq() {
    when(mockCircuitBreaker.allowRequest()).thenReturn(false);
    batchWriter.enqueue(msg(1));
    batchWriter.enqueue(msg(2));

    batchWriter.drainAndWrite();

    verify(mockRepository, never()).batchInsert(any());
    verify(mockDlq, times(2)).add(any());
    verify(mockMetrics).addDropped(2);
  }

  @Test
  void drainAndWrite_success_metricsUpdated() {
    when(mockCircuitBreaker.allowRequest()).thenReturn(true);
    batchWriter.enqueue(msg(1));
    batchWriter.enqueue(msg(2));

    batchWriter.drainAndWrite();

    verify(mockRepository).batchInsert(anyList());
    verify(mockMetrics).addWritten(2);
    verify(mockMetrics).recordFlush(anyLong());
    verify(mockCircuitBreaker).recordSuccess();
    verify(mockDlq, never()).add(any());
  }

  @Test
  void drainAndWrite_failsAllRetries_batchGoesToDlq() {
    // note: this test takes ~600ms due to retry backoff (200ms + 400ms)
    when(mockCircuitBreaker.allowRequest()).thenReturn(true);
    doThrow(new RuntimeException("db down")).when(mockRepository).batchInsert(any());
    batchWriter.enqueue(msg(1));

    batchWriter.drainAndWrite();

    verify(mockRepository, times(3)).batchInsert(any());
    verify(mockCircuitBreaker, times(3)).recordFailure();
    verify(mockDlq).add(any());
    verify(mockMetrics).addDropped(1);
    verify(mockMetrics, never()).addWritten(anyLong());
  }

  @Test
  void drainAndWrite_failsThenSucceeds_writesOnRetry() {
    when(mockCircuitBreaker.allowRequest()).thenReturn(true);
    doThrow(new RuntimeException("transient"))
        .doNothing()
        .when(mockRepository).batchInsert(any());
    batchWriter.enqueue(msg(1));

    batchWriter.drainAndWrite();

    verify(mockRepository, times(2)).batchInsert(any());
    verify(mockMetrics).addWritten(1);
    verify(mockCircuitBreaker).recordSuccess();
    verify(mockDlq, never()).add(any());
  }
}
