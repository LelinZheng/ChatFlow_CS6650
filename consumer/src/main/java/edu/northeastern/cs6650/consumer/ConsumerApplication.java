package edu.northeastern.cs6650.consumer;

import edu.northeastern.cs6650.consumer.config.RabbitMQConfig;
import edu.northeastern.cs6650.consumer.redis.RedisPublisher;
import edu.northeastern.cs6650.consumer.consumer.ConsumerThreadPool;
import edu.northeastern.cs6650.consumer.health.HealthController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Entry point for the consumer application.
 * <p>
 * Starts the Spring Boot context which initializes:
 * <ul>
 *   <li>{@link RabbitMQConfig} — RabbitMQ connection and queue topology</li>
 *   <li>{@link RedisPublisher} — Redis Publish to fan out the messages</li>
 *   <li>{@link ConsumerThreadPool} — consumer threads subscribing to room queues</li>
 *   <li>{@link HealthController} — health check and metrics endpoints</li>
 * </ul>
 */
@SpringBootApplication
public class ConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsumerApplication.class, args);
	}
}
