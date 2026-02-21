package edu.northeastern.cs6650.chat_server.validation;

import edu.northeastern.cs6650.chat_server.model.ClientMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class responsible for validating incoming client message data.
 * <p>
 * This class centralizes validation logic for user identifiers,
 * usernames, and other message-related constraints.
 */
public class MessageValidator {
  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
  private static final int USER_ID_MAX = 100000;

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private MessageValidator() { }

  /**
   * Validates all fields of a {@link ClientMessage}.
   * <p>
   * Rules:
   * <ul>
   *   <li>userId: numeric, 1–100000</li>
   *   <li>username: 3–20 alphanumeric characters (underscores allowed)</li>
   *   <li>message: 1–500 characters (only validated for TEXT type)</li>
   *   <li>timestamp: valid ISO-8601 format</li>
   *   <li>messageType: must not be null</li>
   *   <li>roomId: numeric, 1–20</li>
   * </ul>
   *
   * @param msg the message to validate
   * @return list of error strings; empty if the message is valid
   */
  public static List<String> validate(ClientMessage msg) {
    List<String> errors = new ArrayList<>();

    try {
      int userId = Integer.parseInt(msg.getUserId());
      if (userId <= 0 || userId > USER_ID_MAX) {
        errors.add("userId must be between 1 and " + USER_ID_MAX);
      }
    } catch (Exception e) {
      errors.add("userId must be numeric");
    }

    if (msg.getUsername() == null || !USERNAME_PATTERN.matcher(msg.getUsername()).matches()) {
      errors.add("username must be 3-20 alphanumeric characters");
    }

    if (msg.getMessageType() == null) {
      errors.add("messageType is required");
    } else if (msg.getMessageType().name().equals("TEXT")) {
      if (msg.getMessage() == null
          || msg.getMessage().length() < 1
          || msg.getMessage().length() > 500) {
        errors.add("message must be 1-500 characters for TEXT messages");
      }
    }

    try {
      Instant.parse(msg.getTimestamp());
    } catch (Exception e) {
      errors.add("timestamp must be in ISO-8601 format");
    }

    try {
      int roomId = Integer.parseInt(msg.getRoomId());
      if (roomId < 1 || roomId > 20) {
        errors.add("roomId must be between 1 and 20");
      }
    } catch (Exception e) {
      errors.add("roomId must be numeric");
    }

    return errors;
  }
}
