package edu.northeastern.cs6650.consumer.config;

import edu.northeastern.cs6650.consumer.circuitbreaker.DbCircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for database-related beans.
 *
 * <p>Declares the {@link DbCircuitBreaker} bean, reading thresholds from
 * {@code application.properties} so they can be tuned without recompiling.
 */
@Configuration
public class DatabaseConfig {

  @Value("${db.circuit.failure.threshold:5}")
  private int failureThreshold;

  @Value("${db.circuit.recovery.timeout.ms:30000}")
  private long recoveryTimeoutMs;

  /**
   * Creates the {@link DbCircuitBreaker} bean with configured thresholds.
   *
   * @return a new {@link DbCircuitBreaker} instance
   */
  @Bean
  public DbCircuitBreaker dbCircuitBreaker() {
    return new DbCircuitBreaker(failureThreshold, recoveryTimeoutMs);
  }
}
