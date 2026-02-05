package edu.northeastern.cs6650.client.loadtest;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.worker.MainPhaseMessageGenerator;
import edu.northeastern.cs6650.client.worker.WarmupMessageGenerator;
import edu.northeastern.cs6650.client.ws.ConnectionWorker;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTestRunner {

  private final URI baseUri;

  public LoadTestRunner(URI baseUri) {
    this.baseUri = baseUri;
  }

  public void runWarmup() {
    int warmupThread = 32;
    int warmupMessagesPerThread = 1000;
    int warmupTotalMessages = warmupThread * warmupMessagesPerThread;
    int queueCapacity = 50_000;

    AtomicInteger idx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(warmupThread, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + idx.getAndIncrement());
      return t;
    });

    BlockingQueue<ChatMessage> warmupQueue = new ArrayBlockingQueue<>(queueCapacity);
    Thread generator = new Thread(
        new WarmupMessageGenerator(warmupQueue, warmupTotalMessages),
        "warmup-generator"
    );

    List<ConnectionWorker> senderTasks = new ArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    long start = System.nanoTime();

    generator.start();

    for (int i = 0; i < warmupThread; i++) {

      // Have all messages send to room1 for simplicity
      // maxRetries=5, echoTimeoutMs=5000ms
      URI fullUri = baseUri.resolve("1");
      ConnectionWorker worker = new ConnectionWorker(
          warmupQueue, fullUri, warmupMessagesPerThread,
          5, 5000);
      senderTasks.add(worker);
      futures.add(pool.submit(worker));
    }

    try {
      generator.join();
      for (Future<?> f : futures) {
        f.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      e.getCause().printStackTrace();
    } finally {
      pool.shutdown();
    }

    long end = System.nanoTime();

    int ok = senderTasks.stream().mapToInt(ConnectionWorker::getSentOk).sum();
    int failed = senderTasks.stream().mapToInt(ConnectionWorker::getSentFailed).sum();
    double durationSeconds = (end - start) / 1_000_000_000.0;
    double throughput = ok / durationSeconds;

    System.out.println("Warmup done.");
    System.out.println("OK=" + ok + " failed=" + failed);
    System.out.println("timeSec=" + durationSeconds);
    System.out.println("throughput msg/s=" + throughput);
  }

  public void runMainPhase() {
    int connPerRoom = 2;
    int rooms = 20;
    int mainPhaseThread = connPerRoom * rooms;
    int totalMsg = 500_000;
    int base = totalMsg / mainPhaseThread;
    int rem = totalMsg % mainPhaseThread;
    int queueCapacity = 3000;

    AtomicInteger idx = new AtomicInteger(0);
    ExecutorService pool = Executors.newFixedThreadPool(mainPhaseThread, r -> {
      Thread t = new Thread(r);
      t.setName("sender-" + idx.getAndIncrement());
      return t;
    });

    BlockingQueue<ChatMessage>[] workerQueues = new BlockingQueue[mainPhaseThread];
    for (int i = 0; i < mainPhaseThread; i++) {
      workerQueues[i] = new ArrayBlockingQueue<>(queueCapacity);
    }
    Thread generator = new Thread(
        new MainPhaseMessageGenerator(workerQueues, rooms, connPerRoom, totalMsg),
        "main-generator"
    );

    List<ConnectionWorker> senderTasks = new ArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    long start = System.nanoTime();

    generator.start();

    for (int i = 0; i < mainPhaseThread; i++) {
      int roomId = (i / connPerRoom) + 1;
      URI fullUri = baseUri.resolve(String.valueOf(roomId));
      int expectedForThisWorker = base + (i < rem ? 1 : 0);
      ConnectionWorker worker = new ConnectionWorker(
          workerQueues[i], fullUri, expectedForThisWorker,
          5, 5000);
      senderTasks.add(worker);
      futures.add(pool.submit(worker));
    }

    try {
      generator.join();
      for (Future<?> f : futures) {
        f.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      e.getCause().printStackTrace();
    } finally {
      pool.shutdown();
    }

    long end = System.nanoTime();

    int ok = senderTasks.stream().mapToInt(ConnectionWorker::getSentOk).sum();
    int failed = senderTasks.stream().mapToInt(ConnectionWorker::getSentFailed).sum();
    double durationSeconds = (end - start) / 1_000_000_000.0;
    double throughput = ok / durationSeconds;

    System.out.println("Main phase done.");
    System.out.println("OK=" + ok + " failed=" + failed);
    System.out.println("timeSec=" + durationSeconds);
    System.out.println("throughput msg/s=" + throughput);

  }

  public void printSummary() { }

}
