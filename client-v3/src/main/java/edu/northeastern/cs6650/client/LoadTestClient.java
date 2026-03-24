package edu.northeastern.cs6650.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs6650.client.loadtest.LoadTestRunner;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Entry point for the WebSocket load testing client.
 *
 * <p>This class parses configuration, initializes the {@link LoadTestRunner},
 * and triggers execution of the warmup and main load testing phases.</p>
 *
 * <p>The client is responsible only for orchestration and reporting; all
 * concurrency, message generation, and networking logic is delegated to
 * supporting classes.</p>
 */
public class LoadTestClient {

  private static final int POLL_INTERVAL_MS = 5_000;
  private static final int STABLE_ROUNDS_REQUIRED = 3; // received must not change for 3 polls (15s)
  private static final int MAX_WAIT_MINUTES = 10;

  /**
   * Executes the complete load test workflow.
   *
   * <p>Performs health check, warmup, main phase, and final analysis.
   * Exits with status code 1 if the server health check fails.</p>
   *
   * @param args command-line arguments (currently unused)
   */
  public static void main(String[] args) {
    System.out.println("Load Test Client Started");
//    String serverUrl   = "http://localhost:8080";
//    String consumerUrl = "http://localhost:8081";
//    URI wsBaseUri = URI.create("ws://localhost:8080/chat");
    String serverUrl   = "http://chat-server-v2-ALB-191353243.us-west-2.elb.amazonaws.com";
    String consumerUrl = "http://34.217.76.239:8081";
    URI wsBaseUri = URI.create("ws://chat-server-v2-ALB-191353243.us-west-2.elb.amazonaws.com/chat");

    System.out.println("Performing server health check...");
    if (!checkServerHealth(serverUrl)) {
      System.err.println("Server is not healthy. Aborting load test.");
      System.exit(1);
    }

    System.out.println("Starting load test...");
    LoadTestRunner runner = new LoadTestRunner(wsBaseUri);
    runner.runLoadTest();
    runner.printSummary();

    System.out.println("\nWaiting for consumer to fully drain (RabbitMQ queue + DB buffer)...");
    waitForConsumerDrain(consumerUrl);

    System.out.println("Fetching consumer DB stats...");
    fetchAndLogConsumerStats(consumerUrl, runner.getSummaryPath());

    System.out.println("Fetching server metrics...");
    fetchAndLogMetrics(serverUrl, runner.getSummaryPath());
  }

  /**
   * Polls GET /health/stats on consumer-v3 every 5 seconds until:
   * - db.bufferSize == 0 (BatchWriter has flushed everything to PostgreSQL)
   * - consumer.received has been stable for 3 consecutive polls (RabbitMQ queue drained)
   *
   * <p>Gives up after {@link #MAX_WAIT_MINUTES} minutes and proceeds anyway.
   *
   * @param consumerUrl base URL of consumer-v3 (e.g. "http://localhost:8081")
   */
  private static void waitForConsumerDrain(String consumerUrl) {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    ObjectMapper mapper = new ObjectMapper();

    long deadline = System.currentTimeMillis() + MAX_WAIT_MINUTES * 60_000L;
    long lastReceived = -1;
    int stableRounds = 0;

    while (System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(POLL_INTERVAL_MS);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(consumerUrl + "/health/stats"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
          System.err.println("Consumer stats returned " + resp.statusCode() + ", retrying...");
          continue;
        }

        JsonNode stats = mapper.readTree(resp.body());
        long bufferSize  = stats.path("db.bufferSize").asLong(-1);
        long received    = stats.path("consumer.received").asLong(-1);
        long dlqSize     = stats.path("consumer.broadcastDlqSize").asLong(0);

        System.out.printf("  consumer.received=%d  db.bufferSize=%d  broadcastDlqSize=%d%n",
            received, bufferSize, dlqSize);

        if (received == lastReceived) {
          stableRounds++;
        } else {
          stableRounds = 1;
          lastReceived = received;
        }

        if (bufferSize == 0 && stableRounds >= STABLE_ROUNDS_REQUIRED) {
          System.out.println("Consumer fully drained. Proceeding to metrics.");
          return;
        }

      } catch (IOException | InterruptedException e) {
        System.err.println("Poll failed: " + e.getMessage() + ", retrying...");
      }
    }

    System.err.println("WARNING: consumer did not fully drain within " + MAX_WAIT_MINUTES
        + " minutes. Fetching metrics anyway — counts may be incomplete.");
  }

  /**
   * Fetches final DB write stats from consumer-v3 and appends them to the summary file.
   *
   * @param consumerUrl base URL of consumer-v3 (e.g. "http://localhost:8081")
   * @param summaryPath path to the summary file to append to
   */
  private static void fetchAndLogConsumerStats(String consumerUrl, java.nio.file.Path summaryPath) {
    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(consumerUrl + "/health/stats"))
          .GET()
          .timeout(Duration.ofSeconds(10))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        System.err.println("Consumer stats returned " + response.statusCode());
        return;
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode stats = mapper.readTree(response.body());

      StringBuilder sb = new StringBuilder();
      sb.append("\n=== Consumer DB Stats ===\n");
      sb.append("consumer.received=").append(stats.path("consumer.received").asLong()).append("\n");
      sb.append("consumer.avgProcessingLatencyMs=")
          .append(stats.path("consumer.avgProcessingLatencyMs").asLong()).append("\n");
      sb.append("db.written=").append(stats.path("db.written").asLong()).append("\n");
      sb.append("db.dropped=").append(stats.path("db.dropped").asLong()).append("\n");
      sb.append("db.flushCount=").append(stats.path("db.flushCount").asLong()).append("\n");
      sb.append("db.avgFlushLatencyMs=")
          .append(stats.path("db.avgFlushLatencyMs").asLong()).append("\n");
      sb.append("db.writtenPerSec=").append(stats.path("db.writtenPerSec").asLong()).append("\n");

      System.out.print(sb);

      java.nio.file.Files.writeString(summaryPath, sb.toString(),
          java.nio.file.StandardOpenOption.APPEND);

    } catch (IOException | InterruptedException e) {
      System.err.println("Failed to fetch consumer stats: " + e.getMessage());
    }
  }

  /**
   * Calls GET /api/metrics on server-v3 after the consumer has drained and logs the result.
   * Uses sampleSize=10 to limit the per-message list fields to 10 rows each.
   *
   * @param baseUrl the base HTTP URL of server-v3 (e.g., "http://host:port")
   */
  private static void fetchAndLogMetrics(String baseUrl, java.nio.file.Path summaryPath) {
    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/metrics?sampleSize=1000"))
          .GET()
          .timeout(Duration.ofSeconds(60))
          .build();

      long reqStart = System.currentTimeMillis();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      long reqElapsed = System.currentTimeMillis() - reqStart;

      if (response.statusCode() == 200) {
        String metricsOutput = "\n=== SERVER METRICS ===\n"
            + "metricsApiRoundTripMs=" + reqElapsed + "\n"
            + response.body() + "\n"
            + "======================\n";
        System.out.print(metricsOutput);
        java.nio.file.Files.writeString(summaryPath, metricsOutput,
            java.nio.file.StandardOpenOption.APPEND);
      } else {
        System.err.println("Metrics request failed with status: " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      System.err.println("Failed to fetch metrics: " + e.getMessage());
    }
  }

  /**
   * Performs a health check against the server before starting the load test.
   *
   * @param baseUrl the base HTTP URL of the server (e.g., "http://host:port")
   * @return {@code true} if the server responds with HTTP 200; {@code false} otherwise
   */
  private static boolean checkServerHealth(String baseUrl) {
    try {
      HttpClient client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/health"))
          .GET()
          .timeout(Duration.ofSeconds(10))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        System.out.println("✓ Server health check passed: " + response.body());
        return true;
      } else {
        System.err.println("✗ Server health check failed with status: " + response.statusCode());
        return false;
      }
    } catch (IOException | InterruptedException e) {
      System.err.println("✗ Server health check failed: " + e.getMessage());
      return false;
    }
  }
}
