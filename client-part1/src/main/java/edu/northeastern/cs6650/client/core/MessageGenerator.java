package edu.northeastern.cs6650.client.core;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.util.MessageProducer;
import java.util.concurrent.BlockingQueue;

public class MessageGenerator implements Runnable{
  private BlockingQueue<ChatMessage> messageQueue;
  private int messageCount;
  private MessageProducer producer;
  public MessageGenerator(BlockingQueue<ChatMessage> messageQueue, int messageCount) {
    this.messageQueue = messageQueue;
    this.messageCount = messageCount;
    this.producer = new MessageProducer();
  }

  @Override
  public void run() {
    try {
      for (int i = 0; i < messageCount; i++) {
        messageQueue.put(producer.createMessage()); // blocks if queue is full
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
