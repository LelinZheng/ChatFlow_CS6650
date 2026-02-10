package edu.northeastern.cs6650.client.loadtest;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.generator.MainPhaseMessageGenerator;
import edu.northeastern.cs6650.client.util.MessageFactory;
import edu.northeastern.cs6650.client.ws.ConnectionWorker;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
   * Executes the complete load test including warmup and main phases with connection reuse.
   *
   * <p>This method implements a two-phase load testing strategy designed to minimize
   * connection overhead and avoid TCP connection state issues:</p>
   *
   * <p><strong>Warmup Phase (32 workers):</strong></p>
   * <ul>
   *   <li>Creates 32 WebSocket workers distributed evenly across all 20 rooms</li>
   *   <li>Distribution: rooms 1-12 receive 2 workers each, rooms 13-20 receive 1 worker each</li>
   *   <li>Each worker processes exactly 1000 messages via round-robin distribution</li>
   *   <li>Total: 32,000 messages sent to prime server resources and JVM optimizations</li>
   *   <li>Connections remain open after warmup completes</li>
   * </ul>
   *
   * <p><strong>Main Phase (560 workers, tunable via connPerRoom):</strong></p>
   * <ul>
   *   <li>Reuses all 32 warmup workers (connections already established)</li>
   *   <li>Creates additional workers to reach connPerRoom target per room</li>
   *   <li>New workers are added round-robin across rooms until each room has exactly connPerRoom workers</li>
   *   <li>Processes 500,000 messages with random room assignment (as per spec)</li>
   *   <li>All workers use POISON_PILL termination mode for consistency</li>
   * </ul>
   *
   * <p><strong>Connection Reuse Benefits:</strong></p>
   * <ul>
   *   <li>Avoids TCP TIME_WAIT state issues between phases</li>
   *   <li>Reduces new connection overhead by 32 connections (5.7%)</li>
   *   <li>Aligns with assignment requirement: "persistent connections where possible"</li>
   *   <li>Prevents port exhaustion on client machine</li>
   *   <li>Reduces server connection rate limiting impact</li>
   * </ul>
   *
   * <p>The connPerRoom parameter can be tuned to test different connection densities
   * (e.g., 20, 25, 28, 30 connections per room) while warmup remains constant at 32 workers.</p>
   */
  public void runLoadTest() {
    int rooms = 20;
    int warmupWorkers = 32;
    int warmupMessagesPerWorker = 1000;
    int warmupTotalMessages = warmupWorkers * warmupMessagesPerWorker;

    int connPerRoom = 28; // must be >= 2 in order to reuse the warmup connections
    int mainPhaseWorkers = connPerRoom * rooms;

    int mainPhaseTotalMessages = 500_000;
    int queueCapacity = 3000;

    // Initialize data structures
    int[] workersPerRoom = new int[rooms + 1];

    BlockingQueue<ChatMessage>[] workerQueues = new BlockingQueue[mainPhaseWorkers];
    for (int i = 0; i < mainPhaseWorkers; i++) {
      workerQueues[i] = new ArrayBlockingQueue<>(queueCapacity);
    }

    List<ConnectionWorker> allWorkers = new ArrayList<>();

    AtomicInteger threadIdx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(mainPhaseWorkers, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + threadIdx.getAndIncrement());
      return t;
    });

    // WARMUP PHASE - Create 32 workers evenly distributed
    System.out.println("\n=== Starting Warmup Phase ===");

    long warmupStart = System.nanoTime();

    int workerIndex = 0;

    for (int i = 0; i < warmupWorkers; i++) {
      int roomId = (i % rooms) + 1;
      URI fullUri = baseUri.resolve(String.valueOf(roomId));

      ConnectionWorker worker = new ConnectionWorker(
          workerQueues[workerIndex],
          fullUri,
          5, 5000
      );
      allWorkers.add(worker);
      pool.submit(worker);

      workersPerRoom[roomId]++;
      workerIndex++;
    }

    // Generate warmup messages
    Thread warmupGenerator = new Thread(() -> {
      MessageFactory factory = new MessageFactory();
      try {
        for (int i = 0; i < warmupTotalMessages; i++) {
          ChatMessage msg = factory.createMessage();
          int targetWorker = i % warmupWorkers;
          workerQueues[targetWorker].put(msg);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }, "warmup-generator");

    warmupGenerator.start();

    try {
      warmupGenerator.join();
      waitForQueuesEmpty(workerQueues, 0, warmupWorkers);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    long warmupEnd = System.nanoTime();

    // Calculate warmup stats
    int warmupOk = allWorkers.stream()
        .limit(warmupWorkers)
        .mapToInt(ConnectionWorker::getSentOk)
        .sum();
    int warmupFailed = allWorkers.stream()
        .limit(warmupWorkers)
        .mapToInt(ConnectionWorker::getSentFailed)
        .sum();
    double warmupDuration = (warmupEnd - warmupStart) / 1_000_000_000.0;
    double warmupThroughput = warmupOk / warmupDuration;

    System.out.println("\n=== Warmup Phase Complete ===");
    System.out.println("OK=" + warmupOk + " failed=" + warmupFailed);
    System.out.println("timeSec=" + String.format("%.2f", warmupDuration));
    System.out.println("throughput msg/s=" + String.format("%.2f", warmupThroughput));
    System.out.println("Warmup connections remain open for reuse.");


    // MAIN PHASE - Add workers to reach connPerRoom target
    System.out.println("\n=== Starting Main Phase ===");

    long mainStart = System.nanoTime();

    // Add workers round-robin until each room has connPerRoom workers
    while (workerIndex < mainPhaseWorkers) {
      for (int roomId = 1; roomId <= rooms; roomId++) {
        if (workersPerRoom[roomId] >= connPerRoom) {
          continue;
        }

        URI fullUri = baseUri.resolve(String.valueOf(roomId));

        ConnectionWorker worker = new ConnectionWorker(
            workerQueues[workerIndex],
            fullUri,
            5, 5000
        );
        allWorkers.add(worker);
        pool.submit(worker);

        workersPerRoom[roomId]++;
        workerIndex++;

        if (workerIndex >= mainPhaseWorkers) {
          break;
        }
      }
    }

    System.out.println("\nAll workers ready. Generating main phase messages and sending them...");

    // Generate main phase messages with RANDOM room distribution
    Thread mainGenerator = new Thread(
        new MainPhaseMessageGenerator(workerQueues, rooms, connPerRoom, mainPhaseTotalMessages),
        "main-generator"
    );
    mainGenerator.start();

    try {
      mainGenerator.join();
      waitForQueuesEmpty(workerQueues, 0, mainPhaseWorkers);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    long mainEnd = System.nanoTime();

    // Send poison pills to ALL workers to terminate them
    for (int i = 0; i < mainPhaseWorkers; i++) {
      try {
        workerQueues[i].put(ChatMessage.poison());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // Shutdown thread pool
    pool.shutdown();
    try {
      if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
        System.err.println("Pool did not terminate in time, forcing shutdown");
        pool.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      pool.shutdownNow();
    }

    // Calculate and report statistics
    int totalOk = allWorkers.stream().mapToInt(ConnectionWorker::getSentOk).sum();
    int totalFailed = allWorkers.stream().mapToInt(ConnectionWorker::getSentFailed).sum();
    int mainOk = totalOk - warmupOk; // Subtract warmup to get main phase only
    int mainFailed = totalFailed - warmupFailed;

    double mainDuration = (mainEnd - mainStart) / 1_000_000_000.0;
    double mainThroughput = mainOk / mainDuration;

    int totalConnections = allWorkers.stream().mapToInt(ConnectionWorker::getOpens).sum();
    int totalReconnects = allWorkers.stream().mapToInt(ConnectionWorker::getReconnects).sum();
    int totalConnectionFailures = allWorkers.stream()
        .mapToInt(ConnectionWorker::getConnectionFailures).sum();
    int deadWorkers = (int) allWorkers.stream()
        .filter(w -> w.getOpens() == 0)
        .count();
    int lostMessages = mainPhaseTotalMessages - mainOk - mainFailed;

    System.out.println("\n=== Main Phase Results ===");
    System.out.println("OK=" + mainOk + " failed=" + mainFailed);
    System.out.println("timeSec=" + String.format("%.2f", mainDuration));
    System.out.println("throughput msg/s=" + String.format("%.2f", mainThroughput));
    System.out.println("connections=" + totalConnections);
    System.out.println("reconnections=" + totalReconnects);
    System.out.println("connectionFailures=" + totalConnectionFailures);
    System.out.println("deadWorkers=" + deadWorkers);
    System.out.println("warmupConnectionsReused=" + warmupWorkers);
    if (lostMessages != 0) {
      System.out.println("⚠️  messagesLost=" + lostMessages);
    }
  }

  /**
   * Waits for a range of worker queues to become empty.
   *
   * <p>This method polls the specified range of queues periodically until all
   * queues are empty, indicating that workers have processed all their assigned
   * messages. This is used to detect phase completion without explicitly joining
   * worker threads, allowing workers to remain alive for connection reuse.</p>
   *
   * <p>The method polls every 100ms to balance responsiveness with CPU usage.</p>
   *
   * @param queues array of worker message queues
   * @param startIdx starting index (inclusive) of the range to check
   * @param endIdx ending index (exclusive) of the range to check
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  private void waitForQueuesEmpty(BlockingQueue<ChatMessage>[] queues,
      int startIdx, int endIdx) throws InterruptedException {
    boolean allEmpty = false;
    while (!allEmpty) {
      allEmpty = true;
      for (int i = startIdx; i < endIdx; i++) {
        if (!queues[i].isEmpty()) {
          allEmpty = false;
          break;
        }
      }
      if (!allEmpty) {
        Thread.sleep(100);
      }
    }
  }

}
