package edu.northeastern.cs6650.consumer.consumer;


import com.rabbitmq.client.Channel;
import edu.northeastern.cs6650.consumer.config.RabbitMQConfig;
import edu.northeastern.cs6650.consumer.websocket.RoomSessionHandler;
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

@Component
@DependsOn("rabbitMQConfig")
public class ConsumerThreadPool {

  private static final Logger log = LoggerFactory.getLogger(ConsumerThreadPool.class);

  private final RabbitMQConfig config;
  private final RoomSessionHandler roomSessionHandler;
  private ExecutorService executor;

  public ConsumerThreadPool(RabbitMQConfig config, RoomSessionHandler roomSessionHandler) {
    this.config = config;
    this.roomSessionHandler = roomSessionHandler;
  }

  @PostConstruct
  public void init() throws Exception {
    int threadCount = config.getConsumerThreadCount();
    int totalRooms = RabbitMQConfig.NUM_ROOMS;

    executor = Executors.newFixedThreadPool(threadCount);

    // distribute rooms across threads: round-robin
    // e.g. 10 threads → 2 rooms each, 20 threads → 1 room each
    // 40+ threads → multiple threads per room (competing consumers,
    //               higher throughput but no ordering guarantee)
    Map<Integer, List<String>> assignments = new HashMap<>();
    for (int roomId = 1; roomId <= totalRooms; roomId++) {
      int threadIndex = (roomId - 1) % threadCount;
      assignments.computeIfAbsent(threadIndex, k -> new ArrayList<>())
          .add("room." + roomId);
    }

    // for threads > NUM_ROOMS, extra threads share rooms with existing threads
    // e.g. 40 threads, 20 rooms: thread 0 and thread 20 both get room.1
    if (threadCount > totalRooms) {
      for (int i = totalRooms; i < threadCount; i++) {
        int mirrorIndex = i % totalRooms; // mirror onto existing room assignments
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

      RoomConsumer consumer = new RoomConsumer(
          channel, assignedQueues, roomSessionHandler);
      executor.submit(consumer);

      log.info("Thread {} assigned queues: {}", i, assignedQueues);
    }

    log.info("ConsumerThreadPool started: threads={} rooms={}", threadCount, totalRooms);
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
    log.info("ConsumerThreadPool shut down");
  }
}
