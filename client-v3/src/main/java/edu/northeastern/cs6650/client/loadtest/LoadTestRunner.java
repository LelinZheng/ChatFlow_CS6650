package edu.northeastern.cs6650.client.loadtest;

import edu.northeastern.cs6650.client.metrics.CsvMetricsWriter;
import edu.northeastern.cs6650.client.metrics.MetricRecord;
import edu.northeastern.cs6650.client.metrics.MetricsAnalyzer;
import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.generator.MessageGenerator;
import edu.northeastern.cs6650.client.util.RoomMembershipTracker;
import edu.northeastern.cs6650.client.ws.ConnectionWorker;
import java.net.URI;
import java.nio.file.Files;
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
 * Orchestrates the execution of the WebSocket load test.
 *
 * <p>All workers share a single {@link BlockingQueue}. A configurable number of
 * worker threads compete to pull messages from the queue and send them over their
 * own persistent WebSocket connection to {@code /chat}. The room for each message
 * is encoded in the message body rather than the URI, so workers are free to send
 * to any room in sequence without reconnecting.</p>
 *
 * <p>The total worker count is controlled by {@link #TOTAL_WORKERS} and should be
 * tuned across the values recommended in the assignment (64, 128, 256, 512) to find
 * the optimal throughput point for the deployment under test.</p>
 *
 * <p>There is no warmup phase. The test runs a single main phase of
 * {@link #TOTAL_MESSAGES} messages, after which one poison-pill per worker is
 * enqueued to trigger graceful shutdown.</p>
 *
 * <p>Output files are automatically labeled with the worker count so successive
 * runs do not overwrite each other. For a run with 256 workers, output files will be
 * {@code main_metrics_256w.csv}, {@code throughput_10s_256w.csv}, and
 * {@code summary_256w.txt}.</p>
 */
public class LoadTestRunner {

  /**
   * Total number of concurrent WebSocket worker threads.
   * Tunable value: 64 / 128 / 256 / 512 to find peak throughput.
   */
  private static final int TOTAL_WORKERS = 256;

  /** Total number of messages sent during the load test. */
  private static final int TOTAL_MESSAGES = 500_000;

  /** Number of chat rooms messages are distributed across. */
  private static final int ROOMS = 20;

  /**
   * Capacity of the shared message queue.
   * Large enough to keep all workers busy without consuming excessive memory.
   */
  private static final int QUEUE_CAPACITY = 10_000;

  private final URI serverUri;

  /** Short label appended to all output file names for this run (e.g., {@code "256w"}). */
  private final String runLabel;

  /**
   * Constructs a LoadTestRunner targeting the given WebSocket endpoint.
   *
   * @param serverUri the WebSocket URI for the {@code /chat} endpoint
   *                  (e.g., {@code ws://host/chat})
   */
  public LoadTestRunner(URI serverUri) {
    this.serverUri = serverUri;
    this.runLabel = TOTAL_WORKERS + "w";
  }

  /**
   * Executes the load test.
   *
   * <p>All {@link #TOTAL_WORKERS} workers are started and begin blocking on the
   * shared queue. The message generator then fills the queue with
   * {@link #TOTAL_MESSAGES} messages (each with a random room ID) followed by one
   * poison pill per worker. Workers terminate upon receiving their poison pill.
   * Timing starts when the generator starts and ends when all workers have
   * finished.</p>
   */
  public void runLoadTest() {
    BlockingQueue<ChatMessage> sharedQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    List<ConnectionWorker> workers = new ArrayList<>(TOTAL_WORKERS);
    BlockingQueue<MetricRecord> metricsQueue = new ArrayBlockingQueue<>(50_000);
    Path outDir = Paths.get("..", "results/v2");

    // Start metrics writer — file labeled with worker count so runs don't overwrite each other
    Path mainCsvPath = outDir.resolve("main_metrics_" + runLabel + ".csv");
    Thread metricsWriter = new Thread(
        new CsvMetricsWriter(metricsQueue, mainCsvPath), "metrics-writer");
    metricsWriter.start();

    AtomicInteger threadIdx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(TOTAL_WORKERS, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + threadIdx.getAndIncrement());
      return t;
    });

    // Shared tracker — all workers validate and update membership concurrently
    RoomMembershipTracker membershipTracker = new RoomMembershipTracker();

    System.out.println("\n=== Starting Load Test ===");
    System.out.println("workers=" + TOTAL_WORKERS
        + " messages=" + TOTAL_MESSAGES
        + " rooms=" + ROOMS);

    // Create and submit all workers sharing the single queue and membership tracker
    for (int i = 0; i < TOTAL_WORKERS; i++) {
      ConnectionWorker worker = new ConnectionWorker(
          sharedQueue, serverUri, 5, 5000, metricsQueue, membershipTracker);
      workers.add(worker);
      pool.submit(worker);
    }

    long mainStart = System.nanoTime();

    // Generator produces TOTAL_MESSAGES messages then one poison pill per worker
    Thread generator = new Thread(
        new MessageGenerator(sharedQueue, ROOMS, TOTAL_MESSAGES, TOTAL_WORKERS),
        "message-generator");
    generator.start();

    try {
      generator.join();
      pool.shutdown();
      if (!pool.awaitTermination(120, TimeUnit.SECONDS)) {
        System.err.println("Workers did not terminate in time, forcing shutdown");
        pool.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      pool.shutdownNow();
    }

    long mainEnd = System.nanoTime();

    // Stop metrics writer
    try {
      metricsQueue.put(MetricRecord.poison());
      metricsWriter.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Report results
    int totalOk = workers.stream().mapToInt(ConnectionWorker::getSentOk).sum();
    int totalFailed = workers.stream().mapToInt(ConnectionWorker::getSentFailed).sum();
    double duration = (mainEnd - mainStart) / 1_000_000_000.0;
    double throughput = totalOk / duration;

    int totalConnections = workers.stream().mapToInt(ConnectionWorker::getOpens).sum();
    int totalReconnects = workers.stream().mapToInt(ConnectionWorker::getReconnects).sum();
    int totalConnectionFailures = workers.stream()
        .mapToInt(ConnectionWorker::getConnectionFailures).sum();
    int deadWorkers = (int) workers.stream().filter(w -> w.getOpens() == 0).count();
    int lostMessages = TOTAL_MESSAGES - totalOk - totalFailed;

    StringBuilder results = new StringBuilder();
    results.append("\n=== Load Test Results (").append(runLabel).append(") ===\n");
    results.append("workers=").append(TOTAL_WORKERS)
        .append(" messages=").append(TOTAL_MESSAGES)
        .append(" rooms=").append(ROOMS).append("\n");
    results.append("OK=").append(totalOk).append(" failed=").append(totalFailed).append("\n");
    results.append("timeSec=").append(String.format("%.2f", duration)).append("\n");
    results.append("throughput msg/s=").append(String.format("%.2f", throughput)).append("\n");
    results.append("connections=").append(totalConnections).append("\n");
    results.append("reconnections=").append(totalReconnects).append("\n");
    results.append("connectionFailures=").append(totalConnectionFailures).append("\n");
    results.append("deadWorkers=").append(deadWorkers).append("\n");
    if (lostMessages != 0) {
      results.append("WARNING messagesLost=").append(lostMessages).append("\n");
    }

    System.out.print(results);

    // Write results block to summary file (creates/overwrites per run)
    Path summaryPath = outDir.resolve("summary_" + runLabel + ".txt");
    try {
      Files.createDirectories(outDir);
      Files.writeString(summaryPath, results.toString());
    } catch (Exception e) {
      System.err.println("Failed to write summary file: " + e.getMessage());
    }
  }

  /**
   * Prints a summary of load test results by reading and analyzing the CSV metrics file.
   */
  public void printSummary() {
    System.out.println("\nLoad test summary:");
    Path outDir = Paths.get("..", "results/v2");
    Path metricsCsv = outDir.resolve("main_metrics_" + runLabel + ".csv");
    Path bucketsCsv = outDir.resolve("throughput_10s_" + runLabel + ".csv");
    Path summaryPath = outDir.resolve("summary_" + runLabel + ".txt");
    try {
      new MetricsAnalyzer().analyzeAndSave(metricsCsv, bucketsCsv, summaryPath);
    } catch (Exception e) {
      System.err.println("Failed to analyze metrics: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
