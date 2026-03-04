package edu.northeastern.cs6650.client.loadtest;

import edu.northeastern.cs6650.client.metrics.CsvMetricsWriter;
import edu.northeastern.cs6650.client.metrics.MetricRecord;
import edu.northeastern.cs6650.client.metrics.MetricsAnalyzer;
import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.generator.MainPhaseMessageGenerator;
import edu.northeastern.cs6650.client.util.RoomMembershipTracker;
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
 */
public class LoadTestRunner {

  /**
   * Total number of concurrent WebSocket worker threads.
   * Tune this value across 64 / 128 / 256 / 512 to find peak throughput.
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

  /**
   * Constructs a LoadTestRunner targeting the given WebSocket endpoint.
   *
   * @param serverUri the WebSocket URI for the {@code /chat} endpoint
   *                  (e.g., {@code ws://host/chat})
   */
  public LoadTestRunner(URI serverUri) {
    this.serverUri = serverUri;
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
    Path outDir = Paths.get("..", "results");

    // Start metrics writer
    Path mainCsvPath = outDir.resolve("main_metrics.csv");
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
        new MainPhaseMessageGenerator(sharedQueue, ROOMS, TOTAL_MESSAGES, TOTAL_WORKERS),
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

    System.out.println("\n=== Load Test Results ===");
    System.out.println("OK=" + totalOk + " failed=" + totalFailed);
    System.out.println("timeSec=" + String.format("%.2f", duration));
    System.out.println("throughput msg/s=" + String.format("%.2f", throughput));
    System.out.println("connections=" + totalConnections);
    System.out.println("reconnections=" + totalReconnects);
    System.out.println("connectionFailures=" + totalConnectionFailures);
    System.out.println("deadWorkers=" + deadWorkers);
    if (lostMessages != 0) {
      System.out.println("WARNING messagesLost=" + lostMessages);
    }
  }

  /**
   * Prints a summary of load test results by reading and analyzing the CSV metrics file.
   */
  public void printSummary() {
    System.out.println("\nLoad test summary:");
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
