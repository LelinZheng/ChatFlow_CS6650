package edu.northeastern.cs6650.chat_server;

import static org.mockito.Mockito.mock;

import edu.northeastern.cs6650.chat_server.config.ChannelPool;
import edu.northeastern.cs6650.chat_server.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest
class ChatServerApplicationTests {

	@TestConfiguration
	static class MockRabbitConfig {

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
	}

	@Test
	void contextLoads() {
		// Passes if the Spring context loads without errors
	}
}
