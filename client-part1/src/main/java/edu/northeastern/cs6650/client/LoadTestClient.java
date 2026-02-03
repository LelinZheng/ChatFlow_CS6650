package edu.northeastern.cs6650.client;
import edu.northeastern.cs6650.client.worker.MessageGenerator;
import edu.northeastern.cs6650.client.worker.MessageSender;
import edu.northeastern.cs6650.client.model.ChatMessage;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class LoadTestClient {
  public static void main(String[] args) {
    System.out.println("Load Test Client Started");
    URI baseUri = URI.create("ws://ec2-34-220-155-145.us-west-2.compute.amazonaws.com:8080/chat/");
    warmup(baseUri);
  }

  public static void warmup(URI baseUri) {
    int warmupThread = 32;
    int warmupMessagesPerThread = 1000;
    int warmupTotalMessages = warmupThread * warmupMessagesPerThread;
    int queueCapacity = 50_000;

    BlockingQueue<ChatMessage> warmupQueue = new ArrayBlockingQueue<>(queueCapacity);
    Thread generator = new Thread(new MessageGenerator(warmupQueue, warmupTotalMessages));

    List<MessageSender> senderTasks = new ArrayList<>();
    List<Thread> senderThreads = new ArrayList<>();

    long start = System.nanoTime();

    generator.start();

    for (int i = 0; i < warmupThread; i++) {

      // Have all messages send to room1 for simplicity
      URI fullUri = baseUri.resolve("1");
      MessageSender sender = new MessageSender(warmupQueue, fullUri, warmupMessagesPerThread);
      senderTasks.add(sender);

      Thread senderThread = new Thread(sender, "sender-" + i);
      senderThreads.add(senderThread);
      senderThread.start();
    }

    try {
      generator.join();
      for (Thread t : senderThreads) {
        t.join();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    long end = System.nanoTime();

    int ok = senderTasks.stream().mapToInt(MessageSender::getSentOk).sum();
    int failed = senderTasks.stream().mapToInt(MessageSender::getSentFailed).sum();
    double durationSeconds = (end - start) / 1_000_000_000.0;
    double throughput = ok / durationSeconds;

    System.out.println("Warmup done.");
    System.out.println("OK=" + ok + " failed=" + failed);
    System.out.println("timeSec=" + durationSeconds);
    System.out.println("throughput msg/s=" + throughput);

  }

}
