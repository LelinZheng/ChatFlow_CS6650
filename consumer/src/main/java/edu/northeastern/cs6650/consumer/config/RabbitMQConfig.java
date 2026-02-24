package edu.northeastern.cs6650.consumer.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
  private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

  public static final String EXCHANGE_NAME = "chat.exchange";
  public static final int NUM_ROOMS = 20;

  @Value("${rabbitmq.host:localhost}")
  private String host;

  @Value("${rabbitmq.port:5672}")
  private int port;

  @Value("${rabbitmq.username:admin}")
  private String username;

  @Value("${rabbitmq.password:admin123}")
  private String password;

  @Value("${consumer.thread.count:10}")
  private int consumerThreadCount;

  @Value("${consumer.prefetch.count:10}")
  private int prefetchCount;

  private Connection connection;

  public int getConsumerThreadCount() { return consumerThreadCount; }
  public int getPrefetchCount() { return prefetchCount; }

  @PostConstruct
  public void init() throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);

    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(5000);

    connection = factory.newConnection();
    log.info("Consumer connected to RabbitMQ at {}:{}", host, port);

    // re-declare to be safe if Consumer starts up first
    try (Channel ch = connection.createChannel()) {
      ch.exchangeDeclare(EXCHANGE_NAME, "topic", true);

      Map<String, Object> queueArgs = new HashMap<>();
      queueArgs.put("x-message-ttl", 60000);
      queueArgs.put("x-max-length", 10000);

      for (int i = 1; i <= NUM_ROOMS; i++) {
        String queueName = "room." + i;
        ch.queueDeclare(queueName, true, false, false, queueArgs);
        ch.queueBind(queueName, EXCHANGE_NAME, "room." + i);
      }
      log.info("Exchange and queues verified");
    } catch (Exception e) {
      log.error("Failed to verify RabbitMQ topology: {}", e.getMessage());
      throw e;
    }
  }

  public Connection getConnection() { return connection; }

  @PreDestroy
  public void close() throws Exception {
    if (connection != null && connection.isOpen()) {
      connection.close();
      log.info("RabbitMQ connection closed");
    }
  }
}
