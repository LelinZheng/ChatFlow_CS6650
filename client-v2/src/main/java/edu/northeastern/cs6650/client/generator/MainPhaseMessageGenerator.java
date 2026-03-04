package edu.northeastern.cs6650.client.generator;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.util.MessageFactory;
import edu.northeastern.cs6650.client.util.RandomGenerator;
import java.util.concurrent.BlockingQueue;

/**
 * Generates chat messages for the load test and places them on a shared queue.
 *
 * <p>This generator runs in a single dedicated thread and produces a fixed total
 * number of messages. Each message is assigned a uniformly random room ID so that
 * load is spread evenly across all rooms. Messages are placed on the shared
 * {@link BlockingQueue} that all {@code ConnectionWorker} threads compete to drain,
 * which naturally balances work across workers without any explicit room-to-worker
 * pinning.</p>
 *
 * <p>After all messages have been enqueued, one poison-pill message per worker is
 * appended so that every worker exits cleanly when the queue is exhausted.</p>
 */
public class MainPhaseMessageGenerator implements Runnable {

  private final BlockingQueue<ChatMessage> queue;
  private final int rooms;
  private final int messageCount;
  private final int workerCount;
  private final MessageFactory factory;
  private final RandomGenerator randomGenerator;

  /**
   * Constructs a MainPhaseMessageGenerator.
   *
   * @param queue        the shared queue that all workers pull from
   * @param rooms        the number of chat rooms (messages are distributed uniformly across them)
   * @param messageCount the total number of messages to generate
   * @param workerCount  the number of worker threads; one poison pill is enqueued per worker
   */
  public MainPhaseMessageGenerator(BlockingQueue<ChatMessage> queue,
      int rooms,
      int messageCount,
      int workerCount) {
    this.queue = queue;
    this.rooms = rooms;
    this.messageCount = messageCount;
    this.workerCount = workerCount;
    this.factory = new MessageFactory();
    this.randomGenerator = new RandomGenerator();
  }

  /**
   * Generates {@code messageCount} messages with random room IDs and places them on the shared
   * queue, then enqueues one poison pill per worker to signal termination.
   */
  @Override
  public void run() {
    try {
      for (int i = 0; i < messageCount; i++) {
        ChatMessage msg = factory.createMessage();
        int roomId = randomGenerator.generateRandomInteger(1, rooms);
        msg.setRoomId(String.valueOf(roomId));
        queue.put(msg);
      }
      // Signal each worker to stop
      for (int i = 0; i < workerCount; i++) {
        queue.put(ChatMessage.poison());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
