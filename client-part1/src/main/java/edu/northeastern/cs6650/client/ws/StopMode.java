package edu.northeastern.cs6650.client.ws;

/**
 * Defines how a {@link ConnectionWorker} determines when to stop sending messages.
 */
public enum StopMode {
  /**
   * Worker terminates after sending a predefined number of messages.
   * Used during the warmup phase.
   */
  FIXED_COUNT,
  /**
   * Worker terminates when a poison-pill message is received.
   * Used during the main phase where message distribution is randomized.
   */
  POISON_PILL
}
