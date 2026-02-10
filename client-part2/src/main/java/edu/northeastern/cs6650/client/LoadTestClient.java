package edu.northeastern.cs6650.client;
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
//    String httpBaseUrl = "http://localhost:8080";
//    URI wsBaseUri = URI.create("ws://localhost:8080/chat/");
    String httpBaseUrl = "http://ec2-44-243-114-58.us-west-2.compute.amazonaws.com:8080";
    URI wsBaseUri = URI.create("ws://ec2-44-243-114-58.us-west-2.compute.amazonaws.com:8080/chat/");

    System.out.println("Performing server health check...");
    if (!checkServerHealth(httpBaseUrl)) {
      System.err.println("Server is not healthy. Aborting load test.");
      System.exit(1);
    }

    System.out.println("Starting load test...");
    LoadTestRunner runner = new LoadTestRunner(wsBaseUri);
    runner.runLoadTest();
    runner.printSummary();
  }

  /**
   * Performs a health check against the server before starting the load test.
   *
   * <p>This method sends an HTTP GET request to the server's /health endpoint
   * to verify that the server is reachable and operational before initiating the WebSocket load
   * test.</p>
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
