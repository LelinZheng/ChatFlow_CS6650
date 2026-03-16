package edu.northeastern.cs6650.consumer.db;

import edu.northeastern.cs6650.consumer.model.ChatMessage;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory dead letter queue for messages that could not be persisted to PostgreSQL.
 *
 * <p>Messages land here in two cases:
 * <ul>
 *   <li>The shared buffer is full (producer overflow)</li>
 *   <li>All DB write retries were exhausted or the circuit breaker is open</li>
 * </ul>
 *
 * <p>Every arrival is logged so drops are visible during load tests.
 */
@Component
public class DeadLetterQueue {

  private static final Logger log = LoggerFactory.getLogger(DeadLetterQueue.class);

  private final LinkedBlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();

  /**
   * Adds a message to the dead letter queue.
   * Logs a warning with the message ID on every arrival.
   *
   * @param msg the message that could not be persisted
   */
  public void add(ChatMessage msg) {
    queue.offer(msg);
    log.warn("Message sent to DLQ: {}", msg.getMessageId());
  }

  /**
   * Returns the current number of messages in the dead letter queue.
   *
   * @return DLQ size
   */
  public int size() { return queue.size(); }
}
