package edu.northeastern.cs6650.client.util;

public class RoomSessionGenerator {

  private final MessageProducer producer;
  private final RandomGenerator random;

  public RoomSessionGenerator(MessageProducer producer, RandomGenerator random) {
    this.producer = producer;
    this.random = random;
  }


}
