package edu.northeastern.cs6650.client.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs6650.client.metrics.MetricRecord;
import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.model.MessageType;
import edu.northeastern.cs6650.client.util.RoomMembershipTracker;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Represents a single sender thread responsible for managing one persistent
 * WebSocket connection.
 *
 * <p>Each {@code ConnectionWorker} owns exactly one WebSocket connection and
 * sends messages sequentially using a send-then-wait-for-echo protocol.
 * This guarantees at most one in-flight message per connection and enables
 * accurate latency measurement.</p>
 *
 * <p>Before dispatching each message the worker validates room membership
 * against the shared {@link RoomMembershipTracker}. This is a concurrent-safe
 * runtime gate that protects against out-of-order processing across workers
 * (e.g., a LEAVE processed before a TEXT for the same user/room). Messages that
 * fail the check are counted as failures and recorded with status
 * {@code INVALID_MEMBERSHIP} in the metrics queue.</p>
 */
public class ConnectionWorker implements Runnable {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BlockingQueue<ChatMessage> outbound;
  private final URI serverUri;

  // Echo mailbox: 1-slot handoff for "send -> wait echo"
  private final BlockingQueue<String> echoMailbox = new ArrayBlockingQueue<>(1);

  private final AtomicInteger sentOk = new AtomicInteger(0);
  private final AtomicInteger sentFailed = new AtomicInteger(0);
  private final AtomicInteger reconnects = new AtomicInteger(0);
  private final AtomicInteger opens = new AtomicInteger(0);
  private final AtomicInteger connectionFailures = new AtomicInteger(0);

  private final AtomicLong latencySumNanos = new AtomicLong(0);
  private volatile WebSocketClient client;

  private final int maxRetries;
  private final long echoTimeoutMs;

  BlockingQueue<MetricRecord> metricsQueue;
  private final RoomMembershipTracker membershipTracker;

  /**
   * Creates a connection worker that owns a single persistent WebSocket connection
   * and consumes messages from the provided shared queue.
   *
   * @param outbound          shared queue from which messages are consumed
   * @param serverUri         WebSocket endpoint URI (e.g., {@code ws://host/chat})
   * @param maxRetries        maximum send/echo attempts per message before counting as failed
   * @param echoTimeoutMs     maximum time to wait for an echo acknowledgement (milliseconds)
   * @param metricsQueue      queue to which metric records are published
   * @param membershipTracker shared tracker for concurrent runtime membership validation
   */
  public ConnectionWorker(BlockingQueue<ChatMessage> outbound,
      URI serverUri,
      int maxRetries,
      long echoTimeoutMs,
      BlockingQueue<MetricRecord> metricsQueue,
      RoomMembershipTracker membershipTracker) {
    this.outbound = outbound;
    this.serverUri = serverUri;
    this.maxRetries = maxRetries;
    this.echoTimeoutMs = echoTimeoutMs;
    this.client = buildClient(serverUri);
    this.metricsQueue = metricsQueue;
    this.membershipTracker = membershipTracker;
  }


  /**
   * Builds a new WebSocket client with configured handlers.
   *
   * @param uri the WebSocket endpoint URI
   * @return a new WebSocketClient instance
   */
  private WebSocketClient buildClient(URI uri) {
    return new WebSocketClient(uri) {

      @Override
      public void onOpen(ServerHandshake serverHandshake) {
        opens.incrementAndGet();
      }

      @Override
      public void onMessage(String message) {
        echoMailbox.offer(message);
      }

      @Override
      public void onClose(int i, String s, boolean b) {
      }

      @Override
      public void onError(Exception e) {
      }
    };
  }

  /**
   * Get number of successfully sent messages.
   * @return number of successful sends
   */
  public int getSentOk() {
    return sentOk.get();
  }

  /**
   * Get number of failed messages.
   * @return number of failed sends
   */
  public int getSentFailed() {
    return sentFailed.get();
  }

  /**
   * Get number of reconnections performed.
   * @return number of reconnects
   */
  public int getReconnects() {
    return reconnects.get();
  }

  /**
   * Returns the total number of successful WebSocket connection openings
   * performed by this worker.
   *
   * <p>This counter is incremented every time the underlying WebSocket
   * transitions to the {@code OPEN} state, including the initial connection
   * and any subsequent successful reconnections.</p>
   *
   * @return total number of successful WebSocket {@code onOpen} events
   */
  public int getOpens() {
    return opens.get();
  }

  /**
   * Returns the number of times this worker failed to establish an initial connection.
   *
   * <p>This counter tracks connection failures that occurred during worker startup,
   * not reconnections during message sending.</p>
   *
   * @return number of initial connection failures
   */
  public int getConnectionFailures() {
    return connectionFailures.get();
  }

  /**
   * Get total latency sum in nanoseconds for all successful messages.
   * @return latency sum in nanoseconds
   */
  public long getLatencySumNanos() {
    return latencySumNanos.get();
  }

  /**
   * Executes the main worker loop for this connection.
   *
   * <p>The worker establishes a persistent WebSocket connection and then consumes
   * messages from its assigned outbound queue. Messages are sent sequentially
   * using a send-then-wait-for-echo protocol to ensure at most one in-flight
   * message per connection.</p>
   *
   * <p>Initial connection attempts use exponential backoff (50ms, 100ms, 200ms, 400ms, 800ms)
   * up to 5 retries as specified in the assignment requirements.</p>
   *
   * <p>If the worker fails to establish an initial connection after all retries, it
   * drains its queue and counts all messages as failed.</p>
   *
   * <p>If the thread is interrupted, execution stops and the WebSocket connection
   * is closed.</p>
   */
  @Override
  public void run() {
    boolean connected = false;

    try {
      long backoffMs = 50; // Start at 50ms
      for (int attempt = 1; attempt <= 5; attempt++) {
        try {
          client = buildClient(serverUri);
          connectBlocking();
          connected = true;
          break;
        } catch (InterruptedException e) {
          connectionFailures.incrementAndGet();
          if (attempt < 5) {
            System.err.println("⚠️  Connection attempt " + attempt + "/5 failed for " +
                serverUri + ", retrying in " + backoffMs + "ms... [" +
                Thread.currentThread().getName() + "]");
            Thread.sleep(backoffMs);
            backoffMs *= 2;
          } else {
            System.err.println("❌ All 5 connection attempts failed for " +
                serverUri + " [" + Thread.currentThread().getName() + "]");
          }
        }
      }

      if (!connected) {
        System.err.println("Worker " + Thread.currentThread().getName() +
            " draining queue as failures (never connected)");
        drainQueueAsFailures();
        return;
      }

      while (true) {
          ChatMessage msg = outbound.take();
          if (msg.isPoison()) break;

          if (!validateAndUpdateMembership(msg)) {
            sentFailed.incrementAndGet();
            metricsQueue.offer(new MetricRecord(
                System.currentTimeMillis(),
                msg.getMessageType(),
                -1,
                "INVALID_MEMBERSHIP",
                Integer.parseInt(msg.getRoomId())
            ));
            continue;
          }

          boolean ok = sendWaitEchoWithRetries(msg);
          if (ok) sentOk.incrementAndGet();
          else sentFailed.incrementAndGet();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      safeClose();
    }
  }

  /**
   * Validates a message against the shared {@link RoomMembershipTracker} and updates
   * the tracker state for JOIN and LEAVE messages.
   *
   * <ul>
   *   <li>JOIN: records the user as a member of the room, then returns {@code true}.</li>
   *   <li>TEXT: returns {@code true} only if the user is currently in the room.</li>
   *   <li>LEAVE: returns {@code true} only if the user is currently in the room,
   *       and removes the user from the room before returning.</li>
   * </ul>
   *
   * @param msg the message to validate
   * @return {@code true} if the message should be sent; {@code false} if it should be dropped
   */
  private boolean validateAndUpdateMembership(ChatMessage msg) {
    String userId = msg.getUserId();
    String roomId = msg.getRoomId();
    MessageType type = msg.getMessageType();

    if (type == MessageType.JOIN) {
      membershipTracker.join(userId, roomId);
      return true;
    } else if (type == MessageType.TEXT) {
      return membershipTracker.isMember(userId, roomId);
    } else if (type == MessageType.LEAVE) {
      if (membershipTracker.isMember(userId, roomId)) {
        membershipTracker.leave(userId, roomId);
        return true;
      }
      return false;
    }
    return true;
  }

  /**
   * Drains all messages from the outbound queue and counts them as failures.
   *
   * <p>This method is called when a worker fails to establish an initial
   * connection. It ensures that messages assigned to this dead worker are
   * properly accounted for in failure statistics rather than silently lost.</p>
   *
   * <p>Each drained message is recorded in the metrics queue with a "NO_CONNECTION"
   * status to distinguish connection failures from send failures.</p>
   */
  private void drainQueueAsFailures() {
    System.err.println("DEBUG: drainQueueAsFailures() called for " +
        Thread.currentThread().getName() +
        ", queue size: " + outbound.size());
    int drained = 0;
    try {
      while (true) {
        ChatMessage msg = outbound.poll(); // Non-blocking poll
        if (msg == null) break;
        if (msg.isPoison()) break;

        sentFailed.incrementAndGet();
        drained++;

        // Record in metrics
        metricsQueue.put(new MetricRecord(
            System.currentTimeMillis(),
            msg.getMessageType(),
            -1,
            "NO_CONNECTION",
            Integer.parseInt(msg.getRoomId())
        ));
      }
      if (drained > 0) {
        System.err.println("  Drained " + drained + " messages as failures from " +
            Thread.currentThread().getName());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Establishes the WebSocket connection if it is not already open.
   *
   * <p>This method blocks until the WebSocket handshake completes successfully
   * or times out. It is safe to call multiple times; a connection attempt is
   * only made when the client is not already open.</p>
   *
   * @throws InterruptedException if the current thread is interrupted while
   *                              waiting for the connection to open
   */
  private void connectBlocking() throws InterruptedException {
    if (!client.isOpen()) {
      boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
      if (!connected) {
        throw new InterruptedException("Initial connection timeout after 10 seconds");
      }
    }
  }

  /**
   * Sends a single message over the WebSocket connection and waits for an echo
   * acknowledgement, retrying on failure with exponential backoff.
   *
   * <p>The method enforces strict send ordering by waiting for an echo response
   * before returning. If no echo is received within {@code echoTimeoutMs}, or if
   * a send error occurs, the connection is re-established and the send is retried.</p>
   *
   * <p>Retry policy follows the assignment specification:
   * <ul>
   *   <li>Up to 5 retry attempts per message</li>
   *   <li>Exponential backoff: 50ms, 100ms, 200ms, 400ms between retries</li>
   *   <li>After 5 failed attempts, message is counted as failed</li>
   * </ul>
   * </p>
   *
   * <p>Latency is measured as the time between send initiation and echo receipt
   * and accumulated for later analysis, saved to a {@code metricsQueue}</p>
   *
   * @param message the message to send
   * @return {@code true} if the message was acknowledged by the server;
   *         {@code false} if all retry attempts failed
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  private boolean sendWaitEchoWithRetries(ChatMessage message) throws InterruptedException {
    long backoffMs = 50;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      long t0 = System.nanoTime();
      long sendTsMillis = System.currentTimeMillis();
      try {
        ensureConnected();
        echoMailbox.clear();

        String payload = toJson(message);
        client.send(payload);

        String echo = echoMailbox.poll(echoTimeoutMs, TimeUnit.MILLISECONDS);
        if (echo != null) {
          long t1 = System.nanoTime();
          latencySumNanos.addAndGet(t1 - t0);
          long latencyMillis = TimeUnit.NANOSECONDS.toMillis(t1 - t0);
          metricsQueue.put(
              new MetricRecord(
                  sendTsMillis,
                  message.getMessageType(),
                  latencyMillis,
                  "OK",
                  Integer.parseInt(message.getRoomId())
              )
          );
          return true;
        }

        if (attempt < maxRetries) {
          safeReconnect();
        }
      } catch (Exception e) {
        if (attempt < maxRetries) {
          safeReconnect();
        }
      }

      if (attempt < maxRetries) {
        Thread.sleep(backoffMs);
        backoffMs *= 2;
      }
    }

    try {
      metricsQueue.put(new MetricRecord(
          System.currentTimeMillis(),
          message.getMessageType(),
          -1,
          "FAILED_AFTER_RETRIES",
          Integer.parseInt(message.getRoomId())
      ));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Ensures that a valid, open WebSocket connection exists.
   *
   * <p>If the client is {@code null} or the connection is closed, this method
   * establishes a new connection before returning.</p>
   *
   * @throws InterruptedException if the thread is interrupted while reconnecting
   */
  private void ensureConnected() throws InterruptedException {
    if (client == null) {
      client = buildClient(serverUri);
      boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
      if (!connected) {
        throw new InterruptedException("Connection timeout");
      }
      return;
    }

    if (!client.isOpen()) {
      safeReconnect();
    }
  }

  /**
   * Safely closes the current WebSocket connection and establishes a new one.
   *
   * <p>This method increments the reconnect counter, closes the existing
   * connection, and creates a fresh WebSocket client to avoid stale state
   * or listener issues. The method blocks until the new connection is
   * established or times out.</p>
   *
   * @throws InterruptedException if the thread is interrupted while reconnecting
   */
  private void safeReconnect() throws InterruptedException {
    reconnects.incrementAndGet();

    // Close old connection
    try {
      if (client != null) {
        client.closeBlocking();
      }
    } catch (Exception ignored) {
    }

    // Build fresh client and connect
    client = buildClient(serverUri);
    boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);

    if (!connected) {
      throw new InterruptedException("Reconnection timeout after 10 seconds");
    }
  }

  /**
   * Safely closes the current WebSocket connection and waits for it to complete.
   *
   * <p>This method ensures the WebSocket connection is fully closed before
   * returning, which is critical when transitioning between test phases to
   * avoid resource leaks and connection limit issues.</p>
   */
  private void safeClose() {
    try {
      if (client != null && client.isOpen()) {
        client.closeBlocking();
      }
    } catch (Exception e) {
      // Force close if blocking close fails
      try {
        if (client != null) {
          client.close();
        }
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Serializes a {@link ChatMessage} to its JSON representation.
   *
   * <p>Fields marked with {@code @JsonIgnore} are excluded from the serialized
   * payload.</p>
   *
   * @param msg the message to serialize
   * @return JSON string representation of the message
   * @throws JsonProcessingException if serialization fails
   */
  private String toJson(ChatMessage msg) throws JsonProcessingException {
    return MAPPER.writeValueAsString(msg);
  }
}
