package edu.northeastern.cs6650.chat_server.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe circuit breaker for protecting RabbitMQ publish operations.
 *
 * <p>Implements the standard three-state circuit breaker pattern:
 * <ul>
 *   <li><b>CLOSED</b> — normal operation, all publish attempts go through.
 *       Consecutive failures increment a counter. When the counter reaches
 *       {@code failureThreshold}, the circuit trips to OPEN.</li>
 *   <li><b>OPEN</b> — RabbitMQ is considered unavailable. All requests are
 *       rejected immediately without attempting to publish. After
 *       {@code recoveryTimeoutMs} milliseconds, the circuit transitions to
 *       HALF_OPEN to probe for recovery.</li>
 *   <li><b>HALF_OPEN</b> — one probe request is allowed through. If it
 *       succeeds, the circuit resets to CLOSED. If it fails, the circuit
 *       returns to OPEN and the recovery timeout restarts.</li>
 * </ul>
 *
 * <p>Thread safety is achieved as follows:
 * <ul>
 *   <li>{@link AtomicReference} wraps the {@link State} enum so that reads and
 *       writes to the state field are atomic — equivalent to {@code volatile} but
 *       also enabling compare-and-set operations.</li>
 *   <li>{@code compareAndSet(expected, newValue)} is used for all state transitions.
 *       It atomically checks if the current value equals {@code expected} and only
 *       then sets it to {@code newValue}, returning true if it won the race and false
 *       if another thread changed the state first. This prevents two threads from
 *       simultaneously transitioning the circuit (e.g. two threads both becoming
 *       the HALF_OPEN probe).</li>
 *   <li>{@link AtomicInteger} tracks consecutive failures so that
 *       {@code incrementAndGet()} is atomic across threads.</li>
 * </ul>
 */
public class CircuitBreaker {

  private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

  /**
   * The three states of the circuit breaker.
   */
  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final long recoveryTimeoutMs;

  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger failureCount = new AtomicInteger(0);

  private final AtomicLong openedAt = new AtomicLong(0);

  /**
   * Constructs a CircuitBreaker with configurable thresholds.
   *
   * @param failureThreshold  number of consecutive failures before opening the circuit
   * @param recoveryTimeoutMs milliseconds to wait in OPEN state before trying HALF_OPEN
   */
  public CircuitBreaker(int failureThreshold, long recoveryTimeoutMs) {
    this.failureThreshold = failureThreshold;
    this.recoveryTimeoutMs = recoveryTimeoutMs;
  }

  /**
   * Returns whether a publish attempt should be allowed through.
   *
   * <ul>
   *   <li>CLOSED → always allowed</li>
   *   <li>OPEN → allowed only if the recovery timeout has elapsed (transitions to HALF_OPEN)</li>
   *   <li>HALF_OPEN → only one probe allowed; subsequent calls return false until probe resolves</li>
   * </ul>
   *
   * @return true if the caller should attempt to publish, false if it should be rejected
   */
  public boolean allowRequest() {
    State current = state.get();

    if (current == State.CLOSED) {
      return true;
    }

    if (current == State.OPEN) {
      long elapsed = System.currentTimeMillis() - openedAt.get();
      if (elapsed >= recoveryTimeoutMs) {
        // Recovery timeout elapsed：try to transition to HALF_OPEN
        // compareAndSet ensures only one thread wins this race
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
          log.info("Circuit breaker → HALF_OPEN, probing RabbitMQ after {}ms", elapsed);
          return true; // this thread is the probe
        }
      }
      return false; // still within recovery timeout, or another thread won the race
    }

    if (current == State.HALF_OPEN) {
      return false; // only one thread probes
    }

    return false;
  }

  /**
   * Records a successful publish. Resets the circuit to CLOSED regardless of current state.
   * Clears the failure counter.
   */
  public void recordSuccess() {
    State current = state.get();
    if (current != State.CLOSED) {
      state.set(State.CLOSED);
      log.info("Circuit breaker → CLOSED after successful publish");
    }
    failureCount.set(0);
  }

  /**
   * Records a failed publish attempt.
   * <ul>
   *   <li>CLOSED → increments failure counter; opens circuit if threshold reached</li>
   *   <li>HALF_OPEN → immediately returns to OPEN, restarting the recovery timeout</li>
   *   <li>OPEN → no-op (already open)</li>
   * </ul>
   */
  public void recordFailure() {
    State current = state.get();

    if (current == State.HALF_OPEN) {
      // Probe failed: go back to OPEN and restart the recovery timer
      openedAt.set(System.currentTimeMillis());
      state.set(State.OPEN);
      log.warn("Circuit breaker → OPEN (probe failed, restarting recovery timeout)");
      return;
    }

    if (current == State.CLOSED) {
      int failures = failureCount.incrementAndGet();
      log.warn("Circuit breaker failure {}/{}", failures, failureThreshold);
      if (failures >= failureThreshold) {
        // compareAndSet so only the first thread to hit the threshold opens it
        if (state.compareAndSet(State.CLOSED, State.OPEN)) {
          openedAt.set(System.currentTimeMillis());
          log.error("Circuit breaker → OPEN after {} consecutive failures", failures);
        }
      }
    }
  }

  /**
   * Returns the current state of the circuit breaker.
   *
   * @return current {@link State}
   */
  public State getState() {
    return state.get();
  }

}
