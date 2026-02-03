package edu.northeastern.cs6650.client.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.northeastern.cs6650.client.model.ChatMessage;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MessengerSender implements Runnable {

  private final BlockingQueue<ChatMessage> queue;
  private final WebSocketClient client;
  private final int maxToSend;
  private static final ObjectMapper ObjectMapper = new ObjectMapper();

  private int sentOk = 0;
  private int sentFailed = 0;

  public MessengerSender(BlockingQueue<ChatMessage> queue, URI serverUri, int maxToSend) {
    this.queue = queue;
    this.maxToSend = maxToSend;

    this.client = new WebSocketClient(serverUri) {

      @Override
      public void onOpen(ServerHandshake serverHandshake) {
        System.out.println(Thread.currentThread().getName() + " connected to " + serverUri);
      }

      @Override
      public void onMessage(String s) {

      }

      @Override
      public void onClose(int i, String s, boolean b) {
        System.out.println(Thread.currentThread().getName() + " closed: " + s);
      }

      @Override
      public void onError(Exception e) {
        System.err.println(Thread.currentThread().getName() + " error: " + e.getMessage());
      }
    };
  }

  public int getSentOk() {
    return sentOk;
  }

  public int getSentFailed() {
    return sentFailed;
  }

  @Override
  public void run() {
    try {
      client.connectBlocking();
      for (int i = 0; i < maxToSend; i++) {
        ChatMessage message = queue.take(); // blocks if queue is empty
        boolean ok = sendWithRetries(message, 5);
        if (ok) sentOk++;
        else sentFailed++;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      try {
        client.closeBlocking();
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private boolean sendWithRetries(ChatMessage message, int maxRetries) throws InterruptedException {
    long backoffMs = 50;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        if (!client.isOpen()) {
          reconnect();
        }

        String payload = toJson(message);
        client.send(payload);
        return true;
      } catch (Exception e) {
        if (attempt == maxRetries)
          return false;
        Thread.sleep(backoffMs);
        backoffMs *= 2; // exponential backoff
      }
    }
    return false;
  }

  private void reconnect() throws InterruptedException {
    try {
      client.close();
    } catch (Exception ignored) { }
    client.reconnectBlocking();
  }

  private String toJson(ChatMessage msg) throws JsonProcessingException {
    String msgStr = ObjectMapper.writeValueAsString(msg);
    return msgStr;
  }
}
