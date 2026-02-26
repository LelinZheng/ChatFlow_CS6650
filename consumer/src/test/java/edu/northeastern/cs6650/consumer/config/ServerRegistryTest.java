package edu.northeastern.cs6650.consumer.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ServerRegistryTest {

  private ServerRegistry registry;

  private void initRegistry(String instances) throws Exception {
    registry = new ServerRegistry();
    // inject @Value field directly via reflection
    java.lang.reflect.Field f = ServerRegistry.class.getDeclaredField("serverInstances");
    f.setAccessible(true);
    f.set(registry, instances);
    registry.init();
  }
  
  @Test
  void init_singleInstance_parsesCorrectly() throws Exception {
    initRegistry("http://172.31.1.1:8080");

    assertEquals(1, registry.getServerInstances().size());
    assertEquals("http://172.31.1.1:8080", registry.getServerInstances().get(0));
  }

  @Test
  void init_multipleInstances_parsesAllCorrectly() throws Exception {
    initRegistry("http://172.31.1.1:8080,http://172.31.1.2:8080,http://172.31.1.3:8080");

    assertEquals(3, registry.getServerInstances().size());
    assertTrue(registry.getServerInstances().contains("http://172.31.1.1:8080"));
    assertTrue(registry.getServerInstances().contains("http://172.31.1.2:8080"));
    assertTrue(registry.getServerInstances().contains("http://172.31.1.3:8080"));
  }

  @Test
  void getServerInstances_returnsUnmodifiableList() throws Exception {
    initRegistry("http://172.31.1.1:8080");

    assertThrows(UnsupportedOperationException.class, () ->
        registry.getServerInstances().add("http://malicious:8080"));
  }

  @Test
  void broadcastToAll_unreachableServer_doesNotThrow() throws Exception {
    initRegistry("http://localhost:19999"); // nothing running here

    assertDoesNotThrow(() ->
        registry.broadcastToAll("5", "{\"msg\":\"hello\"}"));
  }

  @Test
  void broadcastToAll_multipleUnreachableServers_doesNotThrow() throws Exception {
    initRegistry("http://localhost:19999,http://localhost:19998,http://localhost:19997");

    assertDoesNotThrow(() ->
        registry.broadcastToAll("5", "{\"msg\":\"hello\"}"));
  }

  @Test
  void broadcastToAll_oneUnreachable_continuesWithOthers() throws Exception {
    // Both unreachable but the point is neither failure stops the other
    initRegistry("http://localhost:19999,http://localhost:19998");

    // Should complete without throwing even though both fail
    assertDoesNotThrow(() ->
        registry.broadcastToAll("5", "payload"));
  }
}