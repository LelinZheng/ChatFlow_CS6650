package edu.northeastern.cs6650.client.ws;

import static edu.northeastern.cs6650.client.ws.StopMode.FIXED_COUNT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs6650.client.model.ChatMessage;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ConnectionWorker implements Runnable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BlockingQueue<ChatMessage> outbound;
  private final URI serverUri;
  private int maxToSend = 0;
  private final StopMode mode;

  // Echo mailbox: 1-slot handoff for "send -> wait echo"
  private final BlockingQueue<String> echoMailbox = new ArrayBlockingQueue<>(1);

  private final AtomicInteger sentOk = new AtomicInteger(0);
  private final AtomicInteger sentFailed = new AtomicInteger(0);
  private final AtomicInteger reconnects = new AtomicInteger(0);

  private final AtomicLong latencySumNanos = new AtomicLong(0);
  private volatile WebSocketClient client;

  private final int maxRetries;
  private final long echoTimeoutMs;

  public ConnectionWorker(BlockingQueue<ChatMessage> outbound,
      URI serverUri,
      int maxRetries,
      long echoTimeoutMs,
      StopMode stopMode,
      int maxToSend) {
    this.outbound = outbound;
    this.serverUri = serverUri;
    this.maxRetries = maxRetries;
    this.echoTimeoutMs = echoTimeoutMs;
    this.maxToSend = maxToSend;
    this.mode = stopMode;
    this.client = buildClient(serverUri);
  }

  public ConnectionWorker(BlockingQueue<ChatMessage> outbound,
      URI serverUri,
      int maxRetries,
      long echoTimeoutMs,
      StopMode stopMode) {
    this.outbound = outbound;
    this.serverUri = serverUri;
    this.maxRetries = maxRetries;
    this.echoTimeoutMs = echoTimeoutMs;
    this.mode = stopMode;
    this.client = buildClient(serverUri);
  }

  private WebSocketClient buildClient(URI uri) {
    return new WebSocketClient(uri) {

      @Override
      public void onOpen(ServerHandshake serverHandshake) {
        System.out.println(Thread.currentThread().getName() + " connected to " + uri);
      }

      @Override
      public void onMessage(String message) {
        try {
          echoMailbox.put(message);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      @Override
      public void onClose(int i, String s, boolean b) {
        System.out.println(Thread.currentThread().getName() + " closed. " + s);
      }

      @Override
      public void onError(Exception e) {
      }
    };
  }

  public int getSentOk() {
    return sentOk.get();
  }

  public int getSentFailed() {
    return sentFailed.get();
  }

  public int getReconnects() {
    return reconnects.get();
  }

  public long getLatencySumNanos() {
    return latencySumNanos.get();
  }

  @Override
  public void run() {
    try {
      connectBlocking();
      if (mode == FIXED_COUNT) {
        for (int i = 0; i < maxToSend; i++) {
          ChatMessage msg = outbound.take();
          boolean ok = sendWaitEchoWithRetries(msg);
          if (ok) sentOk.incrementAndGet(); else sentFailed.incrementAndGet();
        }
      } else { // POISON_PILL
        while (true) {
          ChatMessage msg = outbound.take();
          if (msg.isPoison()) break;

          boolean ok = sendWaitEchoWithRetries(msg);
          if (ok) sentOk.incrementAndGet(); else sentFailed.incrementAndGet();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      safeClose();
    }
  }

  private void connectBlocking() throws InterruptedException {
    if (client == null)
      client = buildClient(serverUri);
    if (!client.isOpen()) {
      client.connectBlocking();
    }
  }

  private boolean sendWaitEchoWithRetries(ChatMessage message) throws InterruptedException {
    long backoffMs = 50;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      long t0 = System.nanoTime();
      try {
        ensureConnected();
        echoMailbox.clear();

        String payload = toJson(message);
        client.send(payload);

        String echo = echoMailbox.poll(echoTimeoutMs, TimeUnit.MILLISECONDS);
        if (echo != null) {
          long t1 = System.nanoTime();
          latencySumNanos.addAndGet(t1 - t0);
          return true;
        }

        safeReconnect();
      } catch (Exception e) {
        if (attempt == maxRetries)
          return false;
        safeReconnect();
      }

      if (attempt < maxRetries) {
        Thread.sleep(backoffMs);
        backoffMs *= 2;
      }
    }
    return false;
  }

  private void ensureConnected() throws InterruptedException {
    if (client == null) {
      client = buildClient(serverUri);
      client.connectBlocking();
      return;
    }

    if (!client.isOpen()) {
      safeReconnect();
    }
  }

  private void safeReconnect() throws InterruptedException {
    reconnects.incrementAndGet();
    try {
      client.close();
    } catch (Exception ignored) {
    }

    // Build a fresh client instance to avoid stale listener / state
    client = buildClient(serverUri);
    client.reconnectBlocking();
  }

  private void safeClose() {
    try {
      if (client != null)
        client.close();
    } catch (Exception ignored) {
    }
  }

  private String toJson(ChatMessage msg) throws JsonProcessingException {
    return MAPPER.writeValueAsString(msg);
  }
}
