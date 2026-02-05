package edu.northeastern.cs6650.client.metrics;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class CsvMetricsWriter implements Runnable {
  private final BlockingQueue<MetricRecord> metricsQueue;
  private final Path outputPath;

  public CsvMetricsWriter(BlockingQueue<MetricRecord> metricsQueue, Path outputPath) {
    this.metricsQueue = metricsQueue;
    this.outputPath = outputPath;
  }

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
