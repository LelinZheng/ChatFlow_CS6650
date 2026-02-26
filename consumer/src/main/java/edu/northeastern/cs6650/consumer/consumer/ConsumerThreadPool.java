package edu.northeastern.cs6650.consumer.consumer;

import com.rabbitmq.client.Channel;
import edu.northeastern.cs6650.consumer.config.RabbitMQConfig;
import edu.northeastern.cs6650.consumer.config.ServerRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Manages the pool of consumer threads and distributes room queues across them.
 * <p>
 * On startup, creates N threads (configurable via {@code consumer.thread.count})
 * and assigns room queues round-robin. Each thread gets a dedicated RabbitMQ
 * channel and runs a {@link RoomConsumer} that subscribes to its assigned queues.
 * <p>
 * Distribution examples:
 * <pre>
 *   10 threads → 2 rooms per thread
 *   20 threads → 1 room per thread (maximum parallelism, ordering guaranteed)
 *   40 threads → 2 threads per room (competing consumers, higher throughput,
 *                                    no ordering guarantee within a room)
 *   80 threads → 4 threads per room
 * </pre>
 * When thread count exceeds room count, extra threads mirror existing room
 * assignments as competing consumers — RabbitMQ delivers each message to
 * whichever thread is free first.
 * <p>
 * In Design B, each {@link RoomConsumer} calls {@link ServerRegistry} to fan out
 * messages to all server instances via their internal broadcast endpoints,
 * rather than maintaining WebSocket sessions directly.
 */
@Component
@DependsOn("rabbitMQConfig")
public class ConsumerThreadPool {

  private static final Logger log = LoggerFactory.getLogger(ConsumerThreadPool.class);

  private final RabbitMQConfig config;
  private final ServerRegistry serverRegistry;
  private ExecutorService executor;

  /**
   * Constructs the pool with its required dependencies.
   *
   * @param config         RabbitMQ config providing connection and tuning params
   * @param serverRegistry registry of chat server instances to fan out broadcasts to
   */
  public ConsumerThreadPool(RabbitMQConfig config, ServerRegistry serverRegistry) {
    this.config = config;
    this.serverRegistry = serverRegistry;
  }

  /**
   * Initializes the thread pool, distributes room queues, and starts all consumer threads.
   * <p>
   * Each thread receives a dedicated RabbitMQ channel with prefetch configured,
   * ensuring backpressure is applied independently per thread.
   *
   * @throws Exception if channel creation fails
   */
  @PostConstruct
  public void init() throws Exception {
    int threadCount = config.getConsumerThreadCount();
    int totalRooms = RabbitMQConfig.NUM_ROOMS;

    executor = Executors.newFixedThreadPool(threadCount);

    Map<Integer, List<String>> assignments = new HashMap<>();
    for (int roomId = 1; roomId <= totalRooms; roomId++) {
      int threadIndex = (roomId - 1) % threadCount;
      assignments.computeIfAbsent(threadIndex, k -> new ArrayList<>())
          .add("room." + roomId);
    }

    if (threadCount > totalRooms) {
      for (int i = totalRooms; i < threadCount; i++) {
        int mirrorIndex = i % totalRooms;
        List<String> sharedQueues = assignments.get(mirrorIndex);
        assignments.put(i, new ArrayList<>(sharedQueues));
      }
      log.info("Thread count {} exceeds room count {} — " +
              "extra threads share queues as competing consumers (no ordering guarantee)",
          threadCount, totalRooms);
    }

    for (int i = 0; i < threadCount; i++) {
      List<String> assignedQueues = assignments.getOrDefault(i, List.of());
      if (assignedQueues.isEmpty()) continue;

      Channel channel = config.getConnection().createChannel();
      channel.basicQos(config.getPrefetchCount());

      RoomConsumer consumer = new RoomConsumer(channel, assignedQueues, serverRegistry);
      executor.submit(consumer);

      log.info("Thread {} assigned queues: {}", i, assignedQueues);
    }

    log.info("ConsumerThreadPool started: threads={} rooms={}", threadCount, totalRooms);
  }

  /**
   * Shuts down all consumer threads on application shutdown.
   * Triggers {@link Thread#interrupt()} on all running threads,
   * causing each {@link RoomConsumer} to exit its sleep loop cleanly.
   */
  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
    log.info("ConsumerThreadPool shut down");
  }
}