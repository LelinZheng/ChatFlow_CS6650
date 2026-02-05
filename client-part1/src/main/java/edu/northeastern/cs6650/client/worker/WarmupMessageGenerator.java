package edu.northeastern.cs6650.client.worker;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.util.MessageFactory;
import java.util.concurrent.BlockingQueue;

public class WarmupMessageGenerator implements Runnable{
  private BlockingQueue<ChatMessage> messageQueue;
  private int messageCount;
  private final MessageFactory producer;
  public WarmupMessageGenerator(BlockingQueue<ChatMessage> messageQueue, int messageCount) {
    this.messageQueue = messageQueue;
    this.messageCount = messageCount;
    this.producer = new MessageFactory();
  }

  @Override
  public void run() {
    try {
      for (int i = 0; i < messageCount; i++) {
        ChatMessage msg = producer.createMessage();
        msg.setRoomId(1); // all warmup messages go to room 1
        messageQueue.put(msg); // blocks if queue is full
      }
      messageQueue.put(ChatMessage.poison()); // signal completion
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
