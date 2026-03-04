package edu.northeastern.cs6650.client.util;

import java.util.SplittableRandom;

/**
 * Utility class providing helper methods for generating random values.
 *
 * <p>This class is used to produce random integers, select random messages
 * from predefined pools, and support probabilistic message type selection
 * for load testing.</p>
 */
public class RandomGenerator {
  private final SplittableRandom rng = new SplittableRandom();

  /**
   * Generates a random message ID using UUID.
   * @return random message ID string
   */
  public String generateRandomMessageId() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   * Generates a random integer between lower and upper (inclusive).
   * @param lower the lower bound
   * @param upper the upper bound
   * @return random integer between lower and upper
   */
  public int generateRandomInteger(Integer lower, Integer upper) {
    return rng.nextInt(lower, upper + 1);
  }

}
