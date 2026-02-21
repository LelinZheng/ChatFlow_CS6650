
package edu.northeastern.cs6650.chat_server.config;

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
 * Spring configuration class that manages the RabbitMQ connection lifecycle.
 * <p>
 * On startup, establishes a single shared TCP connection to RabbitMQ and
 * declares the topic exchange and all 20 room queues with TTL and size limits.
 * The connection is shared across the app via {@link #getConnection()}.
 */
@Configuration
public class RabbitMQConfig {
  private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

  /** Name of the topic exchange all room messages are published to. */
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

  private Connection connection;

  /**
   * Initializes the RabbitMQ connection and declares exchange + queues.
   * <p>
   * Called automatically by Spring after all {@code @Value} fields are injected.
   * Declares a durable topic exchange and 20 durable room queues, each with:
   * <ul>
   *   <li>60 second message TTL</li>
   *   <li>10,000 message max length</li>
   * </ul>
   * If declaration fails, the exception is rethrown to abort application startup.
   *
   * @throws Exception if the connection or queue declaration fails
   */
  @PostConstruct
  public void init() throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);

    connection = factory.newConnection();
    log.info("Connected to RabbitMQ at {}:{}", host, port);

    try (Channel ch = connection.createChannel()) {
      ch.exchangeDeclare(EXCHANGE_NAME, "topic", true);

      Map<String, Object> queueArgs = new HashMap<>();
      queueArgs.put("x-message-ttl", 60000);   // messages expire after 60 seconds
      queueArgs.put("x-max-length", 10000);     // max 10k messages per queue

      for (int i = 1; i <= NUM_ROOMS; i++) {
        String queueName = "room." + i;
        ch.queueDeclare(queueName, true, false, false, queueArgs);
        ch.queueBind(queueName, EXCHANGE_NAME, "room." + i);
      }
      log.info("Declared exchange and {} queues", NUM_ROOMS);
    } catch (Exception e) {
      log.error("Failed to declare RabbitMQ exchange/queues: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Returns the shared RabbitMQ TCP connection.
   * Used by {@link ChannelPool} to create channels.
   *
   * @return the active RabbitMQ {@link Connection}
   */
  public Connection getConnection() {
    return connection;
  }

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
