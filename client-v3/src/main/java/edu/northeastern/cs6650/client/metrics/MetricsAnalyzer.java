package edu.northeastern.cs6650.client.metrics;

import edu.northeastern.cs6650.client.model.MessageType;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Analyzes load test metrics and generates statistical summaries.
 *
 * <p>This analyzer processes CSV metric files generated during load testing to
 * compute latency statistics (mean, median, percentiles), throughput per room,
 * message type distribution, and time-series throughput data.</p>
 *
 * <p>The analyzer produces both console output for immediate review and a
 * CSV file containing throughput measurements in 10-second time buckets for
 * visualization and further analysis.</p>
 */
public class MetricsAnalyzer {

  /**
   * Reads the metrics CSV and prints summary statistics:
   * mean/median/p95/p99/min/max latency, throughput per room, and message type distribution.
   * Also writes throughput-over-time buckets (10-second windows) to {@code outBucketsCsv}.
   *
   * <p>Expected CSV headers: timestamp, messageType, latencyMs, statusCode, roomId</p>
   *
   * <p>The method computes:
   * <ul>
   *   <li>Latency statistics (mean, median, 95th/99th percentiles, min/max) for successful messages</li>
   *   <li>Success and failure counts</li>
   *   <li>Throughput per room (messages per second)</li>
   *   <li>Message type distribution across all statuses</li>
   *   <li>Time-series throughput in 10-second buckets</li>
   * </ul>
   * </p>
   *
   * @param metricsCsv the path to the input metrics CSV file
   * @param outBucketsCsv the path where throughput bucket CSV will be written
   * @throws Exception if file I/O or CSV parsing fails
   */
  public void analyzeAndPrint(Path metricsCsv, Path outBucketsCsv) throws Exception {

    List<Long> okLatencies = new ArrayList<>(500_000);

    long ok = 0;
    long failed = 0;

    long minTs = Long.MAX_VALUE;
    long maxTs = Long.MIN_VALUE;

    Map<Integer, Long> okCountPerRoom = new HashMap<>();
    Map<MessageType, Long> msgTypeCounts = new EnumMap<>(MessageType.class);
    Map<Long, Integer> bucketCounts = new HashMap<>();

    try (Reader reader = Files.newBufferedReader(metricsCsv)) {
      Iterable<CSVRecord> records = CSVFormat.DEFAULT
          .withFirstRecordAsHeader()
          .parse(reader);

      for (CSVRecord r : records) {
        long ts = Long.parseLong(r.get("timestamp"));
        String status = r.get("statusCode");
        int roomId = Integer.parseInt(r.get("roomId"));

        minTs = Math.min(minTs, ts);
        maxTs = Math.max(maxTs, ts);

        // 10-second bucket
        long bucketStart = (ts / 10_000L) * 10_000L;
        bucketCounts.merge(bucketStart, 1, Integer::sum);

        // message type distribution
        String mt = r.get("messageType");
        if (mt != null && !mt.isEmpty()) {
          MessageType type = MessageType.valueOf(mt);
          msgTypeCounts.merge(type, 1L, Long::sum);
        }

        if ("OK".equals(status)) {
          ok++;
          long latencyRaw = Long.parseLong(r.get("latencyMs"));
          okLatencies.add(latencyRaw);
          okCountPerRoom.merge(roomId, 1L, Long::sum);
        } else {
          failed++;
        }
      }
    }

    writeThroughputBuckets(outBucketsCsv, bucketCounts);

    // ---- Latency stats (OK only) ----
    Collections.sort(okLatencies);

    double meanMs = meanMs(okLatencies);
    double medianMs = percentileMs(okLatencies, 50);
    double p95Ms = percentileMs(okLatencies, 95);
    double p99Ms = percentileMs(okLatencies, 99);
    double minMs = okLatencies.isEmpty() ? 0 : okLatencies.get(0);
    double maxMs = okLatencies.isEmpty() ? 0 : okLatencies.get(okLatencies.size() - 1);

    double durationSec = (maxTs > minTs) ? ((maxTs - minTs) / 1000.0) : 1.0;

    // ---- Print summary ----
    System.out.println("=== Metrics Summary ===");
    System.out.println("OK=" + ok + " failed=" + failed);
    System.out.printf("durationSec=%.3f%n", durationSec);

    System.out.println("=== Latency (OK only, ms) ===");
    System.out.printf("mean=%.3f%n", meanMs);
    System.out.printf("median=%.3f%n", medianMs);
    System.out.printf("p95=%.3f%n", p95Ms);
    System.out.printf("p99=%.3f%n", p99Ms);
    System.out.printf("min=%.3f max=%.3f%n", minMs, maxMs);

    System.out.println("=== Throughput per room (OK only, msg/s) ===");
    Map<Integer, Double> throughputPerRoom = new TreeMap<>();
    for (Map.Entry<Integer, Long> e : okCountPerRoom.entrySet()) {
      throughputPerRoom.put(e.getKey(), e.getValue() / durationSec);
    }
    for (Map.Entry<Integer, Double> e : throughputPerRoom.entrySet()) {
      System.out.printf("room %d: %.2f%n", e.getKey(), e.getValue());
    }

    System.out.println("=== Message type distribution (all statuses) ===");
    for (Map.Entry<MessageType, Long> e : msgTypeCounts.entrySet()) {
      System.out.printf("%s: %d%n", e.getKey(), e.getValue());
    }

    System.out.println("Throughput buckets CSV: " + outBucketsCsv.toAbsolutePath());
  }

  /**
   * Same as {@link #analyzeAndPrint} but also appends the full analysis to {@code summaryPath}.
   *
   * <p>This is used by {@link edu.northeastern.cs6650.client.loadtest.LoadTestRunner}
   * to persist the analysis alongside the raw CSV files for later comparison across runs.</p>
   *
   * @param metricsCsv    the path to the input metrics CSV file
   * @param outBucketsCsv the path where throughput bucket CSV will be written
   * @param summaryPath   the file to append the analysis output to
   * @throws Exception if file I/O or CSV parsing fails
   */
  public void analyzeAndSave(Path metricsCsv, Path outBucketsCsv, Path summaryPath)
      throws Exception {

    List<Long> okLatencies = new ArrayList<>(500_000);

    long ok = 0;
    long failed = 0;
    long minTs = Long.MAX_VALUE;
    long maxTs = Long.MIN_VALUE;

    Map<Integer, Long> okCountPerRoom = new HashMap<>();
    Map<MessageType, Long> msgTypeCounts = new EnumMap<>(MessageType.class);
    Map<Long, Integer> bucketCounts = new HashMap<>();

    try (Reader reader = Files.newBufferedReader(metricsCsv)) {
      Iterable<CSVRecord> records = CSVFormat.DEFAULT
          .withFirstRecordAsHeader()
          .parse(reader);

      for (CSVRecord r : records) {
        long ts = Long.parseLong(r.get("timestamp"));
        String status = r.get("statusCode");
        int roomId = Integer.parseInt(r.get("roomId"));

        minTs = Math.min(minTs, ts);
        maxTs = Math.max(maxTs, ts);

        long bucketStart = (ts / 10_000L) * 10_000L;
        bucketCounts.merge(bucketStart, 1, Integer::sum);

        String mt = r.get("messageType");
        if (mt != null && !mt.isEmpty()) {
          MessageType type = MessageType.valueOf(mt);
          msgTypeCounts.merge(type, 1L, Long::sum);
        }

        if ("OK".equals(status)) {
          ok++;
          long latencyRaw = Long.parseLong(r.get("latencyMs"));
          okLatencies.add(latencyRaw);
          okCountPerRoom.merge(roomId, 1L, Long::sum);
        } else {
          failed++;
        }
      }
    }

    writeThroughputBuckets(outBucketsCsv, bucketCounts);

    Collections.sort(okLatencies);

    double meanMs = meanMs(okLatencies);
    double medianMs = percentileMs(okLatencies, 50);
    double p95Ms = percentileMs(okLatencies, 95);
    double p99Ms = percentileMs(okLatencies, 99);
    double minMs = okLatencies.isEmpty() ? 0 : okLatencies.get(0);
    double maxMs = okLatencies.isEmpty() ? 0 : okLatencies.get(okLatencies.size() - 1);

    double durationSec = (maxTs > minTs) ? ((maxTs - minTs) / 1000.0) : 1.0;

    // Build the analysis output into a string so it can go to both stdout and file
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    pw.println("=== Metrics Summary ===");
    pw.println("OK=" + ok + " failed=" + failed);
    pw.printf("durationSec=%.3f%n", durationSec);

    pw.println("=== Latency (OK only, ms) ===");
    pw.printf("mean=%.3f%n", meanMs);
    pw.printf("median=%.3f%n", medianMs);
    pw.printf("p95=%.3f%n", p95Ms);
    pw.printf("p99=%.3f%n", p99Ms);
    pw.printf("min=%.3f max=%.3f%n", minMs, maxMs);

    pw.println("=== Throughput per room (OK only, msg/s) ===");
    Map<Integer, Double> throughputPerRoom = new TreeMap<>();
    for (Map.Entry<Integer, Long> e : okCountPerRoom.entrySet()) {
      throughputPerRoom.put(e.getKey(), e.getValue() / durationSec);
    }
    for (Map.Entry<Integer, Double> e : throughputPerRoom.entrySet()) {
      pw.printf("room %d: %.2f%n", e.getKey(), e.getValue());
    }

    pw.println("=== Message type distribution (all statuses) ===");
    for (Map.Entry<MessageType, Long> e : msgTypeCounts.entrySet()) {
      pw.printf("%s: %d%n", e.getKey(), e.getValue());
    }

    pw.printf("Throughput buckets CSV: %s%n", outBucketsCsv.toAbsolutePath());
    pw.flush();

    String analysis = sw.toString();
    System.out.print(analysis);

    // Append analysis to the summary file (which already has the run results header)
    Files.createDirectories(summaryPath.getParent());
    Files.writeString(summaryPath, analysis, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    System.out.println("Summary saved to: " + summaryPath.toAbsolutePath());
  }

  /**
   * Calculates the arithmetic mean of latency measurements.
   *
   * @param latenciesMs list of latency values in milliseconds
   * @return the mean latency in milliseconds, or 0 if the list is empty
   */
  private static double meanMs(List<Long> latenciesMs) {
    if (latenciesMs.isEmpty()) return 0;
    long sum = 0;
    for (long v : latenciesMs) sum += v;
    return sum / (double) latenciesMs.size();
  }


  /**
   * Calculates the specified percentile of latency measurements using the nearest-rank method.
   *
   * <p>The input list must be sorted in ascending order for accurate results.
   * This method uses the nearest-rank (exclusive) definition of percentiles.</p>
   *
   * @param sortedLatenciesMs list of latency values in milliseconds, sorted in ascending order
   * @param p the percentile to compute (e.g., 50 for median, 95 for p95, 99 for p99)
   * @return the latency value at the specified percentile in milliseconds, or 0 if the list is empty
   */
  private static double percentileMs(List<Long> sortedLatenciesMs, int p) {
    if (sortedLatenciesMs.isEmpty()) return 0;
    int n = sortedLatenciesMs.size();
    int idx = (int) Math.ceil((p / 100.0) * n) - 1; // nearest-rank
    idx = Math.max(0, Math.min(idx, n - 1));
    return sortedLatenciesMs.get(idx);
  }

  /**
   * Writes throughput measurements in 10-second time buckets to a CSV file.
   *
   * <p>Each bucket represents a 10-second window and contains the count of messages
   * processed during that window and the calculated throughput (messages per second).
   * Buckets are written in chronological order.</p>
   *
   * <p>The output CSV has headers: bucketStartMillis, count, throughputMsgPerSec</p>
   *
   * @param outBucketsCsv the path where the throughput bucket CSV will be written
   * @param bucketCounts map of bucket start timestamps (in milliseconds) to message counts
   * @throws Exception if file I/O or CSV writing fails
   */
  private static void writeThroughputBuckets(Path outBucketsCsv, Map<Long, Integer> bucketCounts)
      throws Exception {
    Files.createDirectories(outBucketsCsv.getParent());

    List<Long> keys = new ArrayList<>(bucketCounts.keySet());
    Collections.sort(keys);

    try (var writer = Files.newBufferedWriter(outBucketsCsv);
        CSVPrinter printer = new CSVPrinter(writer,
            CSVFormat.DEFAULT.withHeader("bucketStartMillis", "count", "throughputMsgPerSec"))) {

      for (long bucketStart : keys) {
        int count = bucketCounts.get(bucketStart);
        double tput = count / 10.0; // 10-second bucket
        printer.printRecord(bucketStart, count, tput);
      }
      printer.flush();
    }
  }
}

