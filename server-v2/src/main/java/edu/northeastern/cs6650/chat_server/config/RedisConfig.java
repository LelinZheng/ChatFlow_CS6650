package edu.northeastern.cs6650.chat_server.config;

import edu.northeastern.cs6650.chat_server.redis.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Registers the Redis Pub/Sub listener container.
 * <p>
 * Subscribes {@link RedisSubscriber} to all room channels via the pattern
 * {@code chat:room:*}. Spring auto-connects to Redis on startup and
 * delivers messages to the listener on a background thread.
 */
@Configuration
public class RedisConfig {

  @Bean
  RedisMessageListenerContainer redisListenerContainer(
      RedisConnectionFactory connectionFactory,
      RedisSubscriber subscriber) {

    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, new PatternTopic("chat:room:*"));
    return container;
  }
}
