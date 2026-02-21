
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

@Component
@DependsOn("rabbitMQConfig")
public class ChannelPool {

  private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);

  @Value("${rabbitmq.channel.pool.size:20}")
  private int poolSize;

  private final RabbitMQConfig rabbitMQConfig;
  private BlockingQueue<Channel> pool;

  public ChannelPool(RabbitMQConfig rabbitMQConfig) {
    this.rabbitMQConfig = rabbitMQConfig;
  }

  @PostConstruct
  public void init() throws Exception {
    pool = new ArrayBlockingQueue<>(poolSize);
    for (int i = 0; i < poolSize; i++) {
      pool.offer(rabbitMQConfig.getConnection().createChannel());
    }
    log.info("ChannelPool initialized with {} channels", poolSize);
  }

  public Channel borrowChannel() throws InterruptedException {
    return pool.take(); // blocks until a channel is available
  }

  public void returnChannel(Channel channel) {
    if (channel != null && channel.isOpen()) {
      pool.offer(channel);
    }
  }

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
