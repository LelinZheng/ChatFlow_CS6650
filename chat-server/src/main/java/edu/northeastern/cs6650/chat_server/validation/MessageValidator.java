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
   * Validate the given ClientMessage.
   * @param msg the ClientMessage to validate
   * @return a list of validation error messages, empty if valid
   */
  public static List<String> validate(ClientMessage msg) {
    List<String> errors = new ArrayList<>();

    try{
      int userId = Integer.parseInt(msg.getUserId());
      if (userId <= 0 || userId > USER_ID_MAX) {
        errors.add("user ID must be a positive integer");
      }
    } catch (Exception e) {
      errors.add("user ID must be numeric");
    }

    if (msg.getUsername() == null || !USERNAME_PATTERN.matcher(msg.getUsername()).matches()) {
      errors.add("username must be 3-20 alphanumeric characters");
    }

    if (msg.getMessageType() != null &&
        msg.getMessageType().name().equals("TEXT")) {

      if (msg.getMessage() == null ||
          msg.getMessage().length() < 1 ||
          msg.getMessage().length() > 500) {
        errors.add("message must be 1-500 characters for TEXT messages");
      }
    }

    try {
      Instant.parse(msg.getTimestamp());
    } catch (Exception e) {
      errors.add("timestamp must be in ISO 8601 format");
    }

    if (msg.getMessageType() == null) {
      errors.add("messageType is required");
    }

    return errors;
  }
}
