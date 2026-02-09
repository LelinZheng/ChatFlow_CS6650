package edu.northeastern.cs6650.client.metrics;

import edu.northeastern.cs6650.client.model.MessageType;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public class MetricsAnalyzer {

  /**
   * Reads the metrics CSV and prints summary statistics:
   * mean/median/p95/p99/min/max latency, throughput per room, and message type distribution.
   * Also writes throughput-over-time buckets (10-second windows) to {@code outBucketsCsv}.
   *
   * Expected CSV headers:
   * timestamp,messageType,latencyMs,statusCode,roomId
   *
   * @param metricsCsv metrics CSV path
   * @param outBucketsCsv output CSV path for throughput buckets (10-second)
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

  private static double meanMs(List<Long> latenciesMs) {
    if (latenciesMs.isEmpty()) return 0;
    long sum = 0;
    for (long v : latenciesMs) sum += v;
    return sum / (double) latenciesMs.size();
  }


  private static double percentileMs(List<Long> sortedLatenciesMs, int p) {
    if (sortedLatenciesMs.isEmpty()) return 0;
    int n = sortedLatenciesMs.size();
    int idx = (int) Math.ceil((p / 100.0) * n) - 1; // nearest-rank
    idx = Math.max(0, Math.min(idx, n - 1));
    return sortedLatenciesMs.get(idx);
  }

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
