package edu.northeastern.cs6650.chat_server.validation;

import static org.junit.jupiter.api.Assertions.*;
import edu.northeastern.cs6650.chat_server.model.ClientMessage;
import edu.northeastern.cs6650.chat_server.model.Messagetype;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MessageValidatorTest {

  private ClientMessage validTextMessage() {
    ClientMessage msg = new ClientMessage();
    msg.setUserId("123");
    msg.setUsername("user_name_1");
    msg.setMessage("hello");
    msg.setMessageType(Messagetype.TEXT);
    msg.setTimestamp(Instant.now().toString());
    return msg;
  }

  @Test
  void validate_validTextMessage_returnsEmptyErrors() {
    ClientMessage msg = validTextMessage();

    List<String> errors = MessageValidator.validate(msg);

    assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
  }

  @Test
  void validate_userIdNotNumeric_addsNumericError() {
    ClientMessage msg = validTextMessage();
    msg.setUserId("abc");

    List<String> errors = MessageValidator.validate(msg);

    assertTrue(errors.contains("user ID must be numeric"));
  }

  @Test
  void validate_userIdZeroOrTooLarge_addsPositiveIntegerError() {
    ClientMessage zero = validTextMessage();
    zero.setUserId("0");
    List<String> errorsZero = MessageValidator.validate(zero);
    assertTrue(errorsZero.contains("user ID must be a positive integer"));

    ClientMessage tooBig = validTextMessage();
    tooBig.setUserId("100001");
    List<String> errorsTooBig = MessageValidator.validate(tooBig);
    assertTrue(errorsTooBig.contains("user ID must be a positive integer"));
  }

  @Test
  void validate_usernameNullOrInvalid_addsUsernameError() {
    ClientMessage nullUsername = validTextMessage();
    nullUsername.setUsername(null);
    List<String> errors1 = MessageValidator.validate(nullUsername);
    assertTrue(errors1.contains("username must be 3-20 alphanumeric characters"));

    ClientMessage tooShort = validTextMessage();
    tooShort.setUsername("ab");
    List<String> errors2 = MessageValidator.validate(tooShort);
    assertTrue(errors2.contains("username must be 3-20 alphanumeric characters"));

    ClientMessage badChars = validTextMessage();
    badChars.setUsername("bad-name!");
    List<String> errors3 = MessageValidator.validate(badChars);
    assertTrue(errors3.contains("username must be 3-20 alphanumeric characters"));

    ClientMessage tooLong = validTextMessage();
    tooLong.setUsername("a".repeat(21));
    List<String> errors4 = MessageValidator.validate(tooLong);
    assertTrue(errors4.contains("username must be 3-20 alphanumeric characters"));
  }

  @Test
  void validate_textMessageEmptyOrTooLong_addsMessageLengthError() {
    ClientMessage empty = validTextMessage();
    empty.setMessage("");
    List<String> errors1 = MessageValidator.validate(empty);
    assertTrue(errors1.contains("message must be 1-500 characters for TEXT messages"));

    ClientMessage tooLong = validTextMessage();
    tooLong.setMessage("a".repeat(501));
    List<String> errors2 = MessageValidator.validate(tooLong);
    assertTrue(errors2.contains("message must be 1-500 characters for TEXT messages"));
  }

  @Test
  void validate_nonTextMessageType_doesNotEnforceTextMessageLengthRule() {
    ClientMessage msg = validTextMessage();

    msg.setMessageType(Messagetype.JOIN);
    msg.setMessage(null);

    List<String> errors = MessageValidator.validate(msg);

    assertFalse(errors.contains("message must be 1-500 characters for TEXT messages"));
  }

  @Test
  void validate_timestampInvalid_addsTimestampError() {
    ClientMessage msg = validTextMessage();
    msg.setTimestamp("not-a-timestamp");

    List<String> errors = MessageValidator.validate(msg);

    assertTrue(errors.contains("timestamp must be in ISO 8601 format"));
  }

  @Test
  void validate_messageTypeNull_addsRequiredError() {
    ClientMessage msg = validTextMessage();
    msg.setMessageType(null);

    List<String> errors = MessageValidator.validate(msg);

    assertTrue(errors.contains("messageType is required"));
  }
}
