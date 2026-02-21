package edu.northeastern.cs6650.chat_server.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelPoolTest {

  private ChannelPool pool;
  private RabbitMQConfig mockConfig;
  private Connection mockConnection;

  @BeforeEach
  void setUp() throws Exception {
    mockConnection = mock(Connection.class);
    mockConfig = mock(RabbitMQConfig.class);
    when(mockConfig.getConnection()).thenReturn(mockConnection);

    pool = new ChannelPool(mockConfig);
    setField(pool, "poolSize", 3); // small pool for testing
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private Channel openMockChannel() throws Exception {
    Channel ch = mock(Channel.class);
    when(ch.isOpen()).thenReturn(true);
    when(mockConnection.createChannel()).thenReturn(ch);
    return ch;
  }

  @Test
  void init_createsCorrectNumberOfChannels() throws Exception {
    openMockChannel(); // called 3 times by init
    openMockChannel();
    openMockChannel();

    pool.init();

    // Should have created exactly poolSize channels
    verify(mockConnection, times(3)).createChannel();
  }

  @Test
  void borrowChannel_returnsChannelFromPool() throws Exception {
    Channel ch = openMockChannel();
    when(mockConnection.createChannel()).thenReturn(ch);

    pool.init();

    Channel borrowed = pool.borrowChannel();
    assertNotNull(borrowed);
    assertSame(ch, borrowed);
  }

  @Test
  void returnChannel_putsChannelBackInPool() throws Exception {
    Channel ch = openMockChannel();
    when(mockConnection.createChannel()).thenReturn(ch);
    pool.init();

    Channel borrowed = pool.borrowChannel();
    pool.returnChannel(borrowed);

    // Should be borrowable again
    Channel borrowedAgain = pool.borrowChannel();
    assertSame(ch, borrowedAgain);
  }

  @Test
  void returnChannel_discardsClosedChannel() throws Exception {
    // Use pool size of 1 so we can fully drain it
    setField(pool, "poolSize", 1);
    Channel ch = mock(Channel.class);
    when(ch.isOpen()).thenReturn(false); // closed channel
    when(mockConnection.createChannel()).thenReturn(ch);
    pool.init();

    // Borrow the one channel so pool is empty
    pool.borrowChannel();

    // Try to return a closed channel — should be discarded, not returned to pool
    pool.returnChannel(ch);

    // Pool should still be empty — borrowChannel should block
    ExecutorService exec = Executors.newSingleThreadExecutor();
    Future<Channel> future = exec.submit(() -> pool.borrowChannel());
    assertThrows(TimeoutException.class, () -> future.get(500, TimeUnit.MILLISECONDS));
    exec.shutdownNow();
  }

  @Test
  void returnChannel_doesNothing_whenChannelIsNull() {
    // Should not throw — no pool init needed, just testing null guard
    assertDoesNotThrow(() -> pool.returnChannel(null));
  }

  @Test
  void borrowChannel_blocksUntilChannelReturned() throws Exception {
    Channel ch = openMockChannel();
    when(mockConnection.createChannel()).thenReturn(ch);
    setField(pool, "poolSize", 1);
    pool.init();

    // Borrow the only channel
    Channel borrowed = pool.borrowChannel();

    // Second borrow should block — run on separate thread
    ExecutorService exec = Executors.newSingleThreadExecutor();
    Future<Channel> future = exec.submit(() -> pool.borrowChannel());

    // Confirm it's blocking (not yet done after 200ms)
    assertThrows(TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));

    // Return the channel — unblocks the waiting thread
    pool.returnChannel(borrowed);
    Channel unblocked = future.get(1, TimeUnit.SECONDS);
    assertSame(ch, unblocked);

    exec.shutdown();
  }

  @Test
  void close_closesAllOpenChannels() throws Exception {
    Channel ch1 = mock(Channel.class);
    Channel ch2 = mock(Channel.class);
    Channel ch3 = mock(Channel.class);
    when(ch1.isOpen()).thenReturn(true);
    when(ch2.isOpen()).thenReturn(true);
    when(ch3.isOpen()).thenReturn(false); // already closed

    // Return channels in order
    when(mockConnection.createChannel()).thenReturn(ch1, ch2, ch3);
    pool.init();

    pool.close();

    verify(ch1).close();
    verify(ch2).close();
    verify(ch3, never()).close(); // already closed, should be skipped
  }
}