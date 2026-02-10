package edu.northeastern.cs6650.client.metrics;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes metric records to a CSV file in real-time during load testing.
 *
 * <p>This writer consumes {@link MetricRecord} objects from a blocking queue
 * and writes them to a CSV file with standardized headers. The writer runs in
 * a dedicated thread and continues until a poison-pill record is received,
 * ensuring all metrics are persisted even as the test progresses.</p>
 *
 * <p>The CSV format includes timestamp, message type, latency, status code,
 * and room ID for each recorded message, enabling post-test analysis of
 * performance characteristics.</p>
 */
public class CsvMetricsWriter implements Runnable {
  private final BlockingQueue<MetricRecord> metricsQueue;
  private final Path outputPath;

  /**
   * Constructs a CsvMetricsWriter.
   *
   * @param metricsQueue the blocking queue from which metric records are consumed
   * @param outputPath the file path where the CSV will be written
   */
  public CsvMetricsWriter(BlockingQueue<MetricRecord> metricsQueue, Path outputPath) {
    this.metricsQueue = metricsQueue;
    this.outputPath = outputPath;
  }

  /**
   * Continuously reads metric records from the queue and writes them to CSV.
   *
   * <p>This method blocks waiting for records and writes each one immediately
   * to ensure metrics are persisted even if the test is interrupted. Processing
   * continues until a poison-pill record is received, signaling the end of the
   * test phase.</p>
   *
   * <p>The CSV file is created with headers: timestamp, messageType, latencyMs,
   * statusCode, and roomId.</p>
   */
  @Override
  public void run() {
    try (BufferedWriter writer = Files.newBufferedWriter(outputPath);
        CSVPrinter printer = new CSVPrinter(writer,
            CSVFormat.DEFAULT.withHeader(
                "timestamp",
                "messageType",
                "latencyMs",
                "statusCode",
                "roomId"))) {
      while (true) {
        MetricRecord record = metricsQueue.take();
        if (record.isPoison()) break;
        printer.printRecord(
            record.getTimestampMillis(),
            record.getMessageType(),
            record.getLatencyMillis(),
            record.getStatusCode(),
            record.getRoomId()
        );
      }
      printer.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
