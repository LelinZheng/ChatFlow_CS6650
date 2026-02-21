package edu.northeastern.cs6650.chat_server.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class RabbitMQConfigTest {

  private RabbitMQConfig config;
  private Connection mockConnection;
  private Channel mockChannel;

  @BeforeEach
  void setUp() throws Exception {
    mockChannel = mock(Channel.class);
    mockConnection = mock(Connection.class);
    when(mockConnection.createChannel()).thenReturn(mockChannel);
    when(mockConnection.isOpen()).thenReturn(true);

    // Use a spy so @Value fields can be set directly
    config = spy(new RabbitMQConfig());
    setField(config, "host", "localhost");
    setField(config, "port", 5672);
    setField(config, "username", "admin");
    setField(config, "password", "admin123");
  }

  /** Injects a value into a private field using reflection. */
  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  @Test
  void init_declaresTopicExchangeWithCorrectName() throws Exception {
    // Arrange: mock the ConnectionFactory creation inside init()
    try (MockedConstruction<ConnectionFactory> mocked =
        mockConstruction(ConnectionFactory.class, (factory, ctx) -> {
          when(factory.newConnection()).thenReturn(mockConnection);
        })) {

      config.init();

      verify(mockChannel).exchangeDeclare(
          RabbitMQConfig.EXCHANGE_NAME,
          "topic",
          true  // durable
      );
    }
  }

  @Test
  void init_declares20QueuesWithTtlAndMaxLength() throws Exception {
    try (MockedConstruction<ConnectionFactory> mocked =
        mockConstruction(ConnectionFactory.class, (factory, ctx) -> {
          when(factory.newConnection()).thenReturn(mockConnection);
        })) {

      config.init();

      // Verify all 20 queues declared
      for (int i = 1; i <= 20; i++) {
        String queueName = "room." + i;
        verify(mockChannel).queueDeclare(
            eq(queueName),
            eq(true),   // durable
            eq(false),  // not exclusive
            eq(false),  // not auto-delete
            argThat(args ->
                args != null &&
                    Integer.valueOf(60000).equals(args.get("x-message-ttl")) &&
                    Integer.valueOf(10000).equals(args.get("x-max-length"))
            )
        );
      }
    }
  }

  @Test
  void init_binds20QueuesToExchangeWithCorrectRoutingKeys() throws Exception {
    try (MockedConstruction<ConnectionFactory> mocked =
        mockConstruction(ConnectionFactory.class, (factory, ctx) -> {
          when(factory.newConnection()).thenReturn(mockConnection);
        })) {

      config.init();

      for (int i = 1; i <= 20; i++) {
        verify(mockChannel).queueBind(
            "room." + i,
            RabbitMQConfig.EXCHANGE_NAME,
            "room." + i
        );
      }
    }
  }

  @Test
  void init_throwsAndLogs_whenQueueDeclarationFails() throws Exception {
    doThrow(new IOException("RabbitMQ unavailable"))
        .when(mockChannel).exchangeDeclare(any(String.class), any(String.class), anyBoolean());

    try (MockedConstruction<ConnectionFactory> mocked =
        mockConstruction(ConnectionFactory.class, (factory, ctx) -> {
          when(factory.newConnection()).thenReturn(mockConnection);
        })) {

      // Should rethrow so Spring aborts startup
      assertThrows(IOException.class, () -> config.init());
    }
  }

  @Test
  void close_closesConnectionWhenOpen() throws Exception {
    // Inject the mock connection directly
    Field f = RabbitMQConfig.class.getDeclaredField("connection");
    f.setAccessible(true);
    f.set(config, mockConnection);

    config.close();

    verify(mockConnection).close();
  }

  @Test
  void close_doesNothing_whenConnectionIsNull() {
    // connection field is null by default — should not throw
    assertDoesNotThrow(() -> config.close());
  }
}
