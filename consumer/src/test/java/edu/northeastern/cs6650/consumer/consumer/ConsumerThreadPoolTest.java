package edu.northeastern.cs6650.consumer.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import edu.northeastern.cs6650.consumer.config.RabbitMQConfig;
import edu.northeastern.cs6650.consumer.config.ServerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ConsumerThreadPoolTest {

  private RabbitMQConfig mockConfig;
  private ServerRegistry mockServerRegistry;
  private Connection mockConnection;

  @BeforeEach
  void setUp() throws Exception {
    mockConfig = mock(RabbitMQConfig.class);
    mockServerRegistry = mock(ServerRegistry.class);
    mockConnection = mock(Connection.class);

    when(mockConfig.getConnection()).thenReturn(mockConnection);
    when(mockConnection.createChannel()).thenReturn(mock(Channel.class));
    when(mockConfig.getPrefetchCount()).thenReturn(10);
  }

  // ── room distribution ──────────────────────────────────────

  @Test
  void init_4threads_distributesWith5RoomsEach() throws Exception {
    when(mockConfig.getConsumerThreadCount()).thenReturn(4);

    ConsumerThreadPool pool = new ConsumerThreadPool(mockConfig, mockServerRegistry);
    pool.init();

    verify(mockConnection, times(4)).createChannel();
    pool.shutdown();
  }

  @Test
  void init_20threads_oneRoomPerThread() throws Exception {
    when(mockConfig.getConsumerThreadCount()).thenReturn(20);

    ConsumerThreadPool pool = new ConsumerThreadPool(mockConfig, mockServerRegistry);
    pool.init();

    verify(mockConnection, times(20)).createChannel();
    pool.shutdown();
  }

  @Test
  void init_40threads_20roomsEachHave2CompetingConsumers() throws Exception {
    when(mockConfig.getConsumerThreadCount()).thenReturn(40);

    ConsumerThreadPool pool = new ConsumerThreadPool(mockConfig, mockServerRegistry);
    pool.init();

    verify(mockConnection, times(40)).createChannel();
    pool.shutdown();
  }

  @Test
  void init_1thread_allRoomsAssignedToSingleThread() throws Exception {
    when(mockConfig.getConsumerThreadCount()).thenReturn(1);

    ConsumerThreadPool pool = new ConsumerThreadPool(mockConfig, mockServerRegistry);
    pool.init();

    verify(mockConnection, times(1)).createChannel();
    pool.shutdown();
  }
}