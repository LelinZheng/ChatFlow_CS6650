package edu.northeastern.cs6650.client;
import edu.northeastern.cs6650.client.loadtest.LoadTestRunner;

import java.net.URI;

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
  public static void main(String[] args) {
    System.out.println("Load Test Client Started");
    URI baseUri = URI.create("ws://localhost:8080/chat/");
    // URI baseUri = URI.create("ws://ec2-34-220-155-145.us-west-2.compute.amazonaws.com:8080/chat/");

    LoadTestRunner runner = new LoadTestRunner(baseUri);
    runner.runWarmup();
    runner.runMainPhase();
    runner.printSummary();
  }

}
