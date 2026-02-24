package edu.northeastern.cs6650.consumer.config;

import edu.northeastern.cs6650.consumer.consumer.ConsumerThreadPool;
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

/**
 * Spring configuration class that manages the RabbitMQ connection lifecycle
 * for the consumer application.
 *
 * <p>
 * On startup, establishes a single shared TCP connection to RabbitMQ with
 * automatic recovery enabled. Re-declares the topic exchange and all 20 room
 * queues defensively in case the consumer starts before the server.
 * Exposes consumer tuning parameters (thread count, prefetch count) read from
 * {@code application.properties}.
 * </p>
 */
@Configuration
public class RabbitMQConfig {
  private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

  /** Name of the topic exchange all room messages are published to. */
  public static final String EXCHANGE_NAME = "chat.exchange";

  /** Total number of chat rooms, each backed by its own RabbitMQ queue. */
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

  /**
   * Returns the number of consumer threads to spin up.
   * Configured via {@code consumer.thread.count} in application.properties.
   *
   * @return consumer thread count
   */
  public int getConsumerThreadCount() { return consumerThreadCount; }

  /**
   * Returns the RabbitMQ prefetch count (max unacknowledged messages per channel).
   * Controls backpressure — RabbitMQ will not push more than this many messages
   * to a consumer before some are acknowledged.
   * Configured via {@code consumer.prefetch.count} in application.properties.
   *
   * @return prefetch count
   */
  public int getPrefetchCount() { return prefetchCount; }

  /**
   * Initializes the RabbitMQ connection and verifies exchange and queue topology.
   * <p>
   * Automatic recovery is enabled so the connection self-heals if RabbitMQ
   * drops and restarts. Re-declaration of exchange and queues is idempotent —
   * RabbitMQ ignores re-declarations if settings already match.
   * <p>
   * Queue settings:
   * <ul>
   *   <li>60 second message TTL</li>
   *   <li>10,000 message max length</li>
   * </ul>
   *
   * @throws Exception if the connection or topology verification fails,
   *                   aborting application startup
   */
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

  /**
   * Returns the shared RabbitMQ TCP connection.
   * Used by {@link ConsumerThreadPool} to create per-thread channels.
   *
   * @return the active RabbitMQ {@link Connection}
   */
  public Connection getConnection() { return connection; }

  /**
   * Closes the RabbitMQ connection on application shutdown.
   * Triggered automatically by Spring before the bean is destroyed.
   *
   * @throws Exception if closing the connection fails
   */
  @PreDestroy
  public void close() throws Exception {
    if (connection != null && connection.isOpen()) {
      connection.close();
      log.info("RabbitMQ connection closed");
    }
  }
}
