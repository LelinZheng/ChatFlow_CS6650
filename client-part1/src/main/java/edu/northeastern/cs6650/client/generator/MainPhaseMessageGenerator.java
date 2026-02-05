package edu.northeastern.cs6650.client.generator;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.util.MessageFactory;
import edu.northeastern.cs6650.client.util.RandomGenerator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates chat messages for the main load testing phase.
 *
 * <p>This generator runs in a single dedicated thread and produces a fixed
 * total number of messages. Each message is assigned a random room ID and
 * routed to one of the persistent connection workers for that room using
 * round-robin load balancing.</p>
 *
 * <p>Messages are placed into per-worker blocking queues to ensure that
 * sender threads never wait for message generation while maintaining
 * bounded memory usage.</p>
 */
public class MainPhaseMessageGenerator implements Runnable {
  private final BlockingQueue<ChatMessage>[] workerQueues; // # of workers = rooms * connsPerRoom
  private final int rooms;
  private final int connsPerRoom;
  private final int messageCount;
  private final MessageFactory producer;
  private final RandomGenerator randomGenerator;

  // per-room round robin counter
  private final AtomicInteger[] roundRobinCounters;

  /**
   * Constructs a MainPhaseMessageGenerator.
   * @param workerQueues the per-worker blocking queues
   * @param rooms the number of chat rooms
   * @param connsPerRoom the number of connections per room
   * @param messageCount the total number of messages to generate
   */
  public MainPhaseMessageGenerator(BlockingQueue<ChatMessage>[] workerQueues,
      int rooms,
      int connsPerRoom,
      int messageCount) {
    this.workerQueues = workerQueues;
    this.rooms = rooms;
    this.connsPerRoom = connsPerRoom;
    this.messageCount = messageCount;
    this.producer = new MessageFactory();
    this.randomGenerator = new RandomGenerator();

    this.roundRobinCounters = new AtomicInteger[rooms + 1];
    for (int r = 1; r <= rooms; r++) roundRobinCounters[r] = new AtomicInteger(0);
  }

  /**
   * Generates and routes messages to worker queues.
   * Each message is assigned a random room ID and routed to a worker
   * using round-robin within that room. After all messages are generated,
   * poison pills are sent to each worker to signal completion.
   */
  @Override
  public void run() {
    try {
      for (int i = 0; i < messageCount; i++) {
        ChatMessage msg = producer.createMessage();

        int roomId = pickRoomId();
        msg.setRoomId(roomId);

        int offset = roundRobinCounters[roomId].getAndIncrement() % connsPerRoom;
        int workerIndex = (roomId - 1) * connsPerRoom + offset;

        workerQueues[workerIndex].put(msg); // blocks if that worker queue is full
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // Send poison pills to all workers to signal completion
    for (int w = 0; w < workerQueues.length; w++) {
      try {
        workerQueues[w].put(ChatMessage.poison());
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private int pickRoomId() {
    return randomGenerator.generateRandomInteger(1, rooms);
  }

}
