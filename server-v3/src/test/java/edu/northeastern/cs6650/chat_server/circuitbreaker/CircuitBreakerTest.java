package edu.northeastern.cs6650.chat_server.circuitbreaker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class CircuitBreakerTest {

  private CircuitBreaker breaker;

  @BeforeEach
  void setUp() {
    breaker = new CircuitBreaker(3, 100);
  }

  @Test
  void initialState_isClosed() {
    assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
  }

  @Test
  void allowRequest_whenClosed_returnsTrue() {
    assertTrue(breaker.allowRequest());
  }

  @Test
  void recordFailure_belowThreshold_remainsClosed() {
    breaker.recordFailure();
    breaker.recordFailure();
    assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    assertTrue(breaker.allowRequest());
  }

  @Test
  void recordSuccess_resetsfailureCount() {
    breaker.recordFailure();
    breaker.recordFailure();
    breaker.recordSuccess();
    breaker.recordFailure();
    breaker.recordFailure();
    assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
  }

  @Test
  void recordFailure_atThreshold_opensCircuit() {
    breaker.recordFailure();
    breaker.recordFailure();
    breaker.recordFailure();
    assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
  }

  @Test
  void allowRequest_whenOpen_returnsFalse() {
    tripCircuit();
    assertFalse(breaker.allowRequest());
  }

  @Test
  void recordFailure_whenAlreadyOpen_remainsOpen() {
    tripCircuit();
    breaker.recordFailure();
    assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
  }

  @Test
  void allowRequest_afterRecoveryTimeout_transitionsToHalfOpen() throws InterruptedException {
    tripCircuit();
    Thread.sleep(150);

    assertTrue(breaker.allowRequest());
    assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());
  }

  @Test
  void allowRequest_beforeRecoveryTimeout_remainsOpen() throws InterruptedException {
    tripCircuit();
    Thread.sleep(50);

    assertFalse(breaker.allowRequest());
    assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
  }

  @Test
  void allowRequest_whenHalfOpen_onlyOneProbeAllowed() throws InterruptedException {
    tripCircuit();
    Thread.sleep(150);

    boolean first = breaker.allowRequest();
    boolean second = breaker.allowRequest();

    assertTrue(first);
    assertFalse(second);
  }

  @Test
  void recordSuccess_whenHalfOpen_closesCircuit() throws InterruptedException {
    tripCircuit();
    Thread.sleep(150);

    breaker.allowRequest();
    breaker.recordSuccess();

    assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    assertTrue(breaker.allowRequest());
  }

  @Test
  void recordFailure_whenHalfOpen_reopensCircuit() throws InterruptedException {
    tripCircuit();
    Thread.sleep(150);

    breaker.allowRequest();
    breaker.recordFailure();

    assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
    assertFalse(breaker.allowRequest());
  }

  @Test
  void recordFailure_whenHalfOpen_restartsRecoveryTimeout() throws InterruptedException {
    tripCircuit();
    Thread.sleep(150);

    breaker.allowRequest();
    breaker.recordFailure();

    assertFalse(breaker.allowRequest());

    Thread.sleep(150);
    assertTrue(breaker.allowRequest());
  }

  /** Trips the circuit by recording failures up to the threshold. */
  private void tripCircuit() {
    for (int i = 0; i < 3; i++) {
      breaker.recordFailure();
    }
    assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
  }
}

