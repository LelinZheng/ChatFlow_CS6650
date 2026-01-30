package edu.northeastern.cs6650.client.util;

import java.util.SplittableRandom;

public class RandomGenerator {
  private final SplittableRandom rng = new SplittableRandom();

  public String generateRandomMessageId() {
    return java.util.UUID.randomUUID().toString();
  }

  public int generateRandomInteger(Integer lower, Integer upper) {
    return rng.nextInt(lower, upper + 1);
  }

}
