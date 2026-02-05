package edu.northeastern.cs6650.client.loadtest;

import static edu.northeastern.cs6650.client.ws.StopMode.FIXED_COUNT;
import static edu.northeastern.cs6650.client.ws.StopMode.POISON_PILL;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.worker.MainPhaseMessageGenerator;
import edu.northeastern.cs6650.client.worker.WarmupMessageGenerator;
import edu.northeastern.cs6650.client.ws.ConnectionWorker;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the execution of the WebSocket load test.
 *
 * <p>This class coordinates the warmup phase and the main phase of the client
 * load test, including thread pool setup, message generation, worker lifecycle
 * management, and final metric aggregation.</p>
 *
 * <p>The warmup phase uses a fixed number of threads and messages per thread
 * to prime the server and JVM. The main phase uses persistent WebSocket
 * connections distributed across multiple chat rooms to simulate steady-state
 * traffic.</p>
 *
 * <p>Metrics such as total successful messages, failures, runtime, and
 * throughput are computed after each phase completes.</p>
 */
public class LoadTestRunner {

  private final URI baseUri;

  /**
   * Constructs a LoadTestRunner with the specified base URI for WebSocket connections.
   * @param baseUri
   */
  public LoadTestRunner(URI baseUri) {
    this.baseUri = baseUri;
  }

  /**
   * Executes the warmup phase of the load test.
   *
   * <p>Warmup uses a fixed configuration of {@code 32} sender threads. Each sender
   * establishes its own WebSocket connection (to room {@code 1}) and sends exactly
   * {@code 1000} messages, waiting for an echo acknowledgement before sending the
   * next message. This phase is measured separately to prime server resources and
   * JVM optimizations before the main test.</p>
   *
   * <p>A single {@link WarmupMessageGenerator} thread produces all warmup messages
   * and places them into a shared {@link java.util.concurrent.BlockingQueue}
   * consumed by the sender workers.</p>
   *
   * <p>Upon completion, this method prints total successful sends, failures,
   * runtime, and throughput for the warmup phase.</p>
   */
  public void runWarmup() {
    int warmupThread = 32;
    int warmupMessagesPerThread = 1000;
    int warmupTotalMessages = warmupThread * warmupMessagesPerThread;
    int queueCapacity = 50_000;

    AtomicInteger idx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(warmupThread, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + idx.getAndIncrement());
      return t;
    });

    BlockingQueue<ChatMessage> warmupQueue = new ArrayBlockingQueue<>(queueCapacity);
    Thread generator = new Thread(
        new WarmupMessageGenerator(warmupQueue, warmupTotalMessages),
        "warmup-generator"
    );

    List<ConnectionWorker> senderTasks = new ArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    long start = System.nanoTime();

    generator.start();

    for (int i = 0; i < warmupThread; i++) {

      // Have all messages send to room1 for simplicity
      // maxRetries=5, echoTimeoutMs=5000ms
      URI fullUri = baseUri.resolve("1");
      ConnectionWorker worker = new ConnectionWorker(
          warmupQueue, fullUri,
          5, 5000,
          FIXED_COUNT, warmupMessagesPerThread);
      senderTasks.add(worker);
      futures.add(pool.submit(worker));
    }

    try {
      generator.join();
      for (Future<?> f : futures) {
        f.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      e.getCause().printStackTrace();
    } finally {
      pool.shutdown();
    }

    long end = System.nanoTime();

    int ok = senderTasks.stream().mapToInt(ConnectionWorker::getSentOk).sum();
    int failed = senderTasks.stream().mapToInt(ConnectionWorker::getSentFailed).sum();
    double durationSeconds = (end - start) / 1_000_000_000.0;
    double throughput = ok / durationSeconds;

    System.out.println("Warmup done.");
    System.out.println("OK=" + ok + " failed=" + failed);
    System.out.println("timeSec=" + durationSeconds);
    System.out.println("throughput msg/s=" + throughput);
  }

  /**
   * Executes the main phase of the load test.
   *
   * <p>Main phase simulates steady-state traffic across {@code 20} chat rooms with
   * persistent WebSocket connections. The total message count is {@code 500,000}.
   * Each room is assigned {@code connsPerRoom} connections (one connection per
   * {@link ConnectionWorker}). Messages are generated in a single producer thread
   * and routed to per-worker queues based on random room assignment and round-robin
   * distribution within each room.</p>
   *
   * <p>Workers run in POISON_PILL mode to avoid
   * deadlocks caused by randomized message distribution. The generator inserts one
   * poison pill per worker queue to signal termination.</p>
   *
   * <p>Upon completion, this method prints total successful sends, failures,
   * runtime, and throughput for the main phase.</p>
   */
  public void runMainPhase() {
    int connPerRoom = 2;
    int rooms = 20;
    int mainPhaseThread = connPerRoom * rooms;
    int totalMsg = 500_000;
    int queueCapacity = 3000;

    AtomicInteger idx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(mainPhaseThread, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + idx.getAndIncrement());
      return t;
    });

    BlockingQueue<ChatMessage>[] workerQueues = new BlockingQueue[mainPhaseThread];
    for (int i = 0; i < mainPhaseThread; i++) {
      workerQueues[i] = new ArrayBlockingQueue<>(queueCapacity);
    }

    Thread generator = new Thread(
        new MainPhaseMessageGenerator(workerQueues, rooms, connPerRoom, totalMsg),
        "main-generator"
    );

    List<ConnectionWorker> senderTasks = new ArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    long start = System.nanoTime();

    generator.start();

    for (int i = 0; i < mainPhaseThread; i++) {
      int roomId = (i / connPerRoom) + 1;
      URI fullUri = baseUri.resolve(String.valueOf(roomId));
      ConnectionWorker worker = new ConnectionWorker(
          workerQueues[i], fullUri,
          5, 5000,
          POISON_PILL);
      senderTasks.add(worker);
      futures.add(pool.submit(worker));
    }

    try {
      generator.join();
      for (Future<?> f : futures) {
        f.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      e.getCause().printStackTrace();
    } finally {
      pool.shutdown();
    }

    long end = System.nanoTime();

    int ok = senderTasks.stream().mapToInt(ConnectionWorker::getSentOk).sum();
    int failed = senderTasks.stream().mapToInt(ConnectionWorker::getSentFailed).sum();
    double durationSeconds = (end - start) / 1_000_000_000.0;
    double throughput = ok / durationSeconds;

    System.out.println("Main phase done.");
    System.out.println("OK=" + ok + " failed=" + failed);
    System.out.println("timeSec=" + durationSeconds);
    System.out.println("throughput msg/s=" + throughput);

  }

  /**
   * Prints an aggregated summary of results across all phases of the load test.
   *
   * <p>This method is intended to consolidate warmup and main phase metrics
   * (throughput, failures, latency statistics, etc.) into a single report.</p>
   */
  public void printSummary() { }

}
