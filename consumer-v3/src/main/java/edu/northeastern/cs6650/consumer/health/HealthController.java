package edu.northeastern.cs6650.consumer.health;

import edu.northeastern.cs6650.consumer.db.BatchWriter;
import edu.northeastern.cs6650.consumer.db.DeadLetterQueue;
import edu.northeastern.cs6650.consumer.metrics.BatchMetrics;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing health check and runtime metrics endpoints.
 * <p>
 * {@code GET /health} is used by the AWS ALB to verify the consumer is alive.
 * {@code GET /health/stats} is called by monitoring scripts during load tests
 * to collect consumer state alongside RabbitMQ queue metrics.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  private final BatchMetrics batchMetrics;
  private final DeadLetterQueue dlq;
  private final BatchWriter batchWriter;

  /**
   * Constructs the HealthController with DB write monitoring dependencies.
   *
   * @param batchMetrics counters for DB write throughput and latency
   * @param dlq          dead letter queue for dropped messages
   * @param batchWriter  shared message buffer
   */
  public HealthController(BatchMetrics batchMetrics, DeadLetterQueue dlq, BatchWriter batchWriter) {
    this.batchMetrics = batchMetrics;
    this.dlq = dlq;
    this.batchWriter = batchWriter;
  }

  /**
   * Basic health check — ALB calls this every 30 seconds.
   * Returns 200 OK as long as the consumer is running.
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("timestamp", Instant.now().toString());
    return ResponseEntity.ok(response);
  }

  /**
   * Detailed stats — called by monitoring scripts during load tests.
   * Includes DB write throughput, latency, buffer backpressure, and drop rate.
   */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> stats() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("timestamp", Instant.now().toString());
    response.put("written", batchMetrics.getWritten());
    response.put("dropped", batchMetrics.getDropped());
    response.put("flushCount", batchMetrics.getFlushCount());
    response.put("avgFlushLatencyMs", batchMetrics.getAvgFlushLatencyMs());
    response.put("dlqSize", dlq.size());
    response.put("bufferSize", batchWriter.bufferSize());
    return ResponseEntity.ok(response);
  }
}
