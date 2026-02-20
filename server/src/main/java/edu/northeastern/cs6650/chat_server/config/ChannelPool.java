
package edu.northeastern.cs6650.chat_server.config;

import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A thread-safe pool of RabbitMQ {@link Channel} objects.
 * <p>
 * RabbitMQ channels are lightweight virtual connections multiplexed over a single
 * TCP connection. This pool allows multiple threads to publish messages concurrently
 * without creating a new channel per message or per thread.
 * <p>
 * Threads that call {@link #borrowChannel()} will block if all channels are in use,
 * and resume as soon as one is returned via {@link #returnChannel(Channel)}.
 */
@Component
@DependsOn("rabbitMQConfig")
public class ChannelPool {

  private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);

  @Value("${rabbitmq.channel.pool.size:20}")
  private int poolSize;

  private final RabbitMQConfig rabbitMQConfig;
  private BlockingQueue<Channel> pool;

  /**
   * Constructs the pool with a reference to the shared RabbitMQ connection config.
   *
   * @param rabbitMQConfig the config holding the shared RabbitMQ connection
   */
  public ChannelPool(RabbitMQConfig rabbitMQConfig) {
    this.rabbitMQConfig = rabbitMQConfig;
  }

  /**
   * Pre-creates all channels and fills the pool.
   * Called automatically by Spring after {@link RabbitMQConfig} is initialized
   * (guaranteed by {@code @DependsOn}).
   *
   * @throws Exception if channel creation fails
   */
  @PostConstruct
  public void init() throws Exception {
    pool = new ArrayBlockingQueue<>(poolSize);
    for (int i = 0; i < poolSize; i++) {
      pool.offer(rabbitMQConfig.getConnection().createChannel());
    }
    log.info("ChannelPool initialized with {} channels", poolSize);
  }

  /**
   * Borrows a channel from the pool, blocking until one is available.
   *
   * @return an available RabbitMQ {@link Channel}
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public Channel borrowChannel() throws InterruptedException {
    return pool.take(); // blocks until a channel is available
  }

  /**
   * Returns a channel to the pool so other threads can use it.
   * If the channel is closed or null, it is discarded rather than returned.
   *
   * @param channel the channel to return
   */
  public void returnChannel(Channel channel) {
    if (channel != null && channel.isOpen()) {
      pool.offer(channel);
    }
  }

  /**
   * Closes all channels in the pool on application shutdown.
   * Triggered automatically by Spring before the bean is destroyed.
   */
  @PreDestroy
  public void close() {
    for (Channel ch : pool) {
      try {
        if (ch.isOpen()) ch.close();
      } catch (Exception e) {
        log.warn("Error closing channel: {}", e.getMessage());
      }
    }
  }

}
