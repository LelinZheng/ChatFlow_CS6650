package edu.northeastern.cs6650.consumer.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe circuit breaker for protecting PostgreSQL batch write operations.
 *
 * <p>Implements the standard three-state circuit breaker pattern:
 * <ul>
 *   <li><b>CLOSED</b> — normal operation, all DB write attempts go through.
 *       Consecutive failures increment a counter. When the counter reaches
 *       {@code failureThreshold}, the circuit trips to OPEN.</li>
 *   <li><b>OPEN</b> — the DB is considered unavailable. All requests are
 *       rejected immediately without attempting to write. After
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
 *       writes to the state field are atomic.</li>
 *   <li>{@code compareAndSet(expected, newValue)} is used for all state transitions
 *       to prevent two threads from simultaneously transitioning the circuit.</li>
 *   <li>{@link AtomicInteger} tracks consecutive failures so that
 *       {@code incrementAndGet()} is atomic across threads.</li>
 * </ul>
 */
public class DbCircuitBreaker {

  private static final Logger log = LoggerFactory.getLogger(DbCircuitBreaker.class);

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
   * Constructs a DbCircuitBreaker with configurable thresholds.
   *
   * @param failureThreshold  number of consecutive failures before opening the circuit
   * @param recoveryTimeoutMs milliseconds to wait in OPEN state before trying HALF_OPEN
   */
  public DbCircuitBreaker(int failureThreshold, long recoveryTimeoutMs) {
    this.failureThreshold = failureThreshold;
    this.recoveryTimeoutMs = recoveryTimeoutMs;
  }

  /**
   * Returns whether a DB write attempt should be allowed through.
   *
   * <ul>
   *   <li>CLOSED → always allowed</li>
   *   <li>OPEN → allowed only if the recovery timeout has elapsed (transitions to HALF_OPEN)</li>
   *   <li>HALF_OPEN → only one probe allowed; subsequent calls return false until probe resolves</li>
   * </ul>
   *
   * @return true if the caller should attempt a DB write, false if it should be rejected
   */
  public boolean allowRequest() {
    State current = state.get();

    if (current == State.CLOSED) {
      return true;
    }

    if (current == State.OPEN) {
      long elapsed = System.currentTimeMillis() - openedAt.get();
      if (elapsed >= recoveryTimeoutMs) {
        // compareAndSet ensures only one thread wins this race
        if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
          log.info("DB circuit breaker → HALF_OPEN, probing DB after {}ms", elapsed);
          return true;
        }
      }
      return false;
    }

    if (current == State.HALF_OPEN) {
      return false; // only one thread probes
    }

    return false;
  }

  /**
   * Records a successful DB write. Resets the circuit to CLOSED and clears the failure counter.
   */
  public void recordSuccess() {
    State current = state.get();
    if (current != State.CLOSED) {
      state.set(State.CLOSED);
      log.info("DB circuit breaker → CLOSED after successful write");
    }
    failureCount.set(0);
  }

  /**
   * Records a failed DB write attempt.
   * <ul>
   *   <li>CLOSED → increments failure counter; opens circuit if threshold reached</li>
   *   <li>HALF_OPEN → immediately returns to OPEN, restarting the recovery timeout</li>
   *   <li>OPEN → no-op (already open)</li>
   * </ul>
   */
  public void recordFailure() {
    State current = state.get();

    if (current == State.HALF_OPEN) {
      openedAt.set(System.currentTimeMillis());
      state.set(State.OPEN);
      log.warn("DB circuit breaker → OPEN (probe failed, restarting recovery timeout)");
      return;
    }

    if (current == State.CLOSED) {
      int failures = failureCount.incrementAndGet();
      log.warn("DB circuit breaker failure {}/{}", failures, failureThreshold);
      if (failures >= failureThreshold) {
        if (state.compareAndSet(State.CLOSED, State.OPEN)) {
          openedAt.set(System.currentTimeMillis());
          log.error("DB circuit breaker → OPEN after {} consecutive failures", failures);
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
