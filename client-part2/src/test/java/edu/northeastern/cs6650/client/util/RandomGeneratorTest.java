package edu.northeastern.cs6650.client.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RandomGenerator class.
 */
class RandomGeneratorTest {

  @Test
  void testGenerateRandomMessageIdIsNotNull() {
    RandomGenerator generator = new RandomGenerator();
    String messageId = generator.generateRandomMessageId();

    assertNotNull(messageId);
    assertFalse(messageId.isEmpty());
  }

  @Test
  void testGenerateRandomMessageIdIsUnique() {
    RandomGenerator generator = new RandomGenerator();
    String id1 = generator.generateRandomMessageId();
    String id2 = generator.generateRandomMessageId();

    assertNotEquals(id1, id2);
  }

  @Test
  void testGenerateRandomMessageIdFormat() {
    RandomGenerator generator = new RandomGenerator();
    String messageId = generator.generateRandomMessageId();

    // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    assertTrue(messageId.matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    ));
  }

  @Test
  void testGenerateRandomIntegerInRange() {
    RandomGenerator generator = new RandomGenerator();

    for (int i = 0; i < 100; i++) {
      int value = generator.generateRandomInteger(1, 10);
      assertTrue(value >= 1 && value <= 10,
          "Value " + value + " is out of range [1, 10]");
    }
  }

  @Test
  void testGenerateRandomIntegerLowerBound() {
    RandomGenerator generator = new RandomGenerator();

    boolean foundLowerBound = false;
    for (int i = 0; i < 100; i++) {
      if (generator.generateRandomInteger(1, 10) == 1) {
        foundLowerBound = true;
        break;
      }
    }
    assertTrue(foundLowerBound, "Lower bound never generated");
  }

  @Test
  void testGenerateRandomIntegerUpperBound() {
    RandomGenerator generator = new RandomGenerator();

    boolean foundUpperBound = false;
    for (int i = 0; i < 100; i++) {
      if (generator.generateRandomInteger(1, 10) == 10) {
        foundUpperBound = true;
        break;
      }
    }
    assertTrue(foundUpperBound, "Upper bound never generated");
  }

  @Test
  void testGenerateRandomIntegerSingleValue() {
    RandomGenerator generator = new RandomGenerator();

    // When lower == upper, should always return that value
    for (int i = 0; i < 10; i++) {
      assertEquals(5, generator.generateRandomInteger(5, 5));
    }
  }

  @Test
  void testGenerateRandomIntegerLargeRange() {
    RandomGenerator generator = new RandomGenerator();

    for (int i = 0; i < 100; i++) {
      int value = generator.generateRandomInteger(1, 100000);
      assertTrue(value >= 1 && value <= 100000);
    }
  }

  @Test
  void testGenerateRandomIntegerDistribution() {
    RandomGenerator generator = new RandomGenerator();
    int lower = 1;
    int upper = 10;
    int[] counts = new int[11]; // indices 0-10

    // Generate many values
    for (int i = 0; i < 1000; i++) {
      int value = generator.generateRandomInteger(lower, upper);
      counts[value]++;
    }

    // Each value should appear at least once in 1000 trials
    for (int i = lower; i <= upper; i++) {
      assertTrue(counts[i] > 0,
          "Value " + i + " was never generated");
    }
  }
}