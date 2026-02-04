package edu.northeastern.cs6650.client;
import edu.northeastern.cs6650.client.loadtest.LoadTestRunner;

import java.net.URI;

public class LoadTestClient {
  public static void main(String[] args) {
    System.out.println("Load Test Client Started");
    URI baseUri = URI.create("ws://ec2-34-220-155-145.us-west-2.compute.amazonaws.com:8080/chat/");

    LoadTestRunner runner = new LoadTestRunner(baseUri);
    runner.runWarmup();
    runner.runMainPhase();
    runner.printSummary();
  }

}
