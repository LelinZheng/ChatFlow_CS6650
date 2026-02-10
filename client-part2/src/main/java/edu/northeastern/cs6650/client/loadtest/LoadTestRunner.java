package edu.northeastern.cs6650.client.loadtest;

import edu.northeastern.cs6650.client.metrics.CsvMetricsWriter;
import edu.northeastern.cs6650.client.metrics.MetricRecord;
import edu.northeastern.cs6650.client.metrics.MetricsAnalyzer;
import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.generator.MainPhaseMessageGenerator;
import edu.northeastern.cs6650.client.util.MessageFactory;
import edu.northeastern.cs6650.client.ws.ConnectionWorker;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the execution of the WebSocket load test with connection reuse.
 *
 * <p>This class coordinates warmup and main phases while maintaining persistent
 * WebSocket connections between phases. Warmup workers are evenly distributed
 * across all 20 rooms and remain connected for the main phase. Additional workers
 * are created to reach the target connection density (connPerRoom), avoiding
 * connection churn and TCP TIME_WAIT issues.</p>
 *
 * <p>The main phase uses random room distribution as specified in the assignment,
 * while warmup uses round-robin distribution to ensure each of the 32 workers
 * processes exactly 1000 messages.</p>
 */
public class LoadTestRunner {

  private final URI baseUri;

  /**
   * Constructs a LoadTestRunner with the specified base URI for WebSocket connections.
   *
   * @param baseUri the base WebSocket URI (e.g., ws://host:port/chat/)
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

    BlockingQueue<MetricRecord> metricsQueue = new ArrayBlockingQueue<>(50_000);
    Path outDir = Paths.get("..", "results");

    AtomicInteger threadIdx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(mainPhaseWorkers, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + threadIdx.getAndIncrement());
      return t;
    });

    // WARMUP PHASE - Create 32 workers evenly distributed
    System.out.println("\n=== Starting Warmup Phase ===");

    Path warmupCsvPath = outDir.resolve("warmup_metrics.csv");
    Thread warmupMetricsWriter = new Thread(
        new CsvMetricsWriter(metricsQueue, warmupCsvPath), "warmup-metrics-writer");
    warmupMetricsWriter.start();

    long warmupStart = System.nanoTime();

    int workerIndex = 0;

    for (int i = 0; i < warmupWorkers; i++) {
      int roomId = (i % rooms) + 1;
      URI fullUri = baseUri.resolve(String.valueOf(roomId));

      ConnectionWorker worker = new ConnectionWorker(
          workerQueues[workerIndex],
          fullUri,
          5, 5000,
          metricsQueue
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

    try {
      metricsQueue.put(MetricRecord.poison());
      warmupMetricsWriter.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

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

    int additionalWorkersNeeded = mainPhaseWorkers - warmupWorkers;

    Path mainCsvPath = outDir.resolve("main_metrics.csv");
    Thread mainMetricsWriter = new Thread(
        new CsvMetricsWriter(metricsQueue, mainCsvPath), "main-metrics-writer");
    mainMetricsWriter.start();

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
            5, 5000,
            metricsQueue
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

    // Stop main metrics writer
    try {
      metricsQueue.put(MetricRecord.poison());
      mainMetricsWriter.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
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

  /**
   * Prints an aggregated summary of results across all phases of the load test.
   *
   * <p>This method is intended to consolidate warmup and main phase metrics
   * (throughput, failures, latency statistics, etc.) into a single report.</p>
   */
  public void printSummary () {
    System.out.println("Load test summary:");
    System.out.println("Main phase metrics to be aggregated here.");

    Path outDir = Paths.get("..", "results");
    Path metricsCsv = outDir.resolve("main_metrics.csv");
    Path bucketsCsv = outDir.resolve("throughput_10s.csv");

    try {
      new MetricsAnalyzer().analyzeAndPrint(metricsCsv, bucketsCsv);
    } catch (Exception e) {
      System.err.println("Failed to analyze metrics: " + e.getMessage());
      e.printStackTrace();
    }
  }
}