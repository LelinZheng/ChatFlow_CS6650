package edu.northeastern.cs6650.client.worker;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.util.MessageFactory;
import java.util.concurrent.BlockingQueue;

/**
 * Generates chat messages for the warmup phase of the load test.
 *
 * <p>This generator produces a fixed number of messages and places them into
 * a shared blocking queue consumed by warmup workers. The warmup phase is
 * intended to prime server resources and JVM optimizations before the main
 * performance measurement phase.</p>
 */
public class WarmupMessageGenerator implements Runnable{
  private BlockingQueue<ChatMessage> messageQueue;
  private int messageCount;
  private final MessageFactory producer;
  public WarmupMessageGenerator(BlockingQueue<ChatMessage> messageQueue, int messageCount) {
    this.messageQueue = messageQueue;
    this.messageCount = messageCount;
    this.producer = new MessageFactory();
  }

  /**
   * Generates warmup messages and places them into the shared queue.
   * All messages are assigned to room ID 1 for simplicity.
   * The method blocks if the queue is full.
   */
  @Override
  public void run() {
    try {
      for (int i = 0; i < messageCount; i++) {
        ChatMessage msg = producer.createMessage();
        msg.setRoomId(1); // all warmup messages go to room 1
        messageQueue.put(msg); // blocks if queue is full
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
