package edu.northeastern.cs6650.chat_server;

import static org.mockito.Mockito.mock;

import edu.northeastern.cs6650.chat_server.config.ChannelPool;
import edu.northeastern.cs6650.chat_server.config.RabbitMQConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class ChatServerApplicationTests {

	@TestConfiguration
	static class MockInfraConfig {

		@Bean
		@Primary  // overrides the real RabbitMQConfig bean
		public RabbitMQConfig rabbitMQConfig() {
			return mock(RabbitMQConfig.class);
		}

		@Bean
		@Primary  // overrides the real ChannelPool bean
		public ChannelPool channelPool() {
			return mock(ChannelPool.class);
		}

		@Bean
		@Primary  // overrides the real RedisConfig bean — no Redis needed in tests
		public RedisMessageListenerContainer redisListenerContainer() {
			return mock(RedisMessageListenerContainer.class);
		}

		@Bean
		@Primary  // overrides the real DataSource bean — no Postgres needed in tests
		public DataSource dataSource() {
			return mock(DataSource.class);
		}
	}

	@Test
	void contextLoads() {
		// Passes if the Spring context loads without errors
	}
}
