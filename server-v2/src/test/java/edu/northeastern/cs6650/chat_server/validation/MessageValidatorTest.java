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
    msg.setRoomId("5");  // required — server routes by roomId
    return msg;
  }

  @Test
  void validate_validTextMessage_returnsEmptyErrors() {
    List<String> errors = MessageValidator.validate(validTextMessage());
    assertTrue(errors.isEmpty(), "Expected no validation errors but got: " + errors);
  }

  @Test
  void validate_userIdNotNumeric_addsNumericError() {
    ClientMessage msg = validTextMessage();
    msg.setUserId("abc");

    assertTrue(MessageValidator.validate(msg).contains("userId must be numeric"));
  }

  @Test
  void validate_userIdZeroOrTooLarge_addsRangeError() {
    ClientMessage zero = validTextMessage();
    zero.setUserId("0");
    assertTrue(MessageValidator.validate(zero).contains("userId must be between 1 and 100000"));

    ClientMessage tooBig = validTextMessage();
    tooBig.setUserId("100001");
    assertTrue(MessageValidator.validate(tooBig).contains("userId must be between 1 and 100000"));
  }

  @Test
  void validate_usernameNullOrInvalid_addsUsernameError() {
    String expected = "username must be 3-20 alphanumeric characters";

    ClientMessage nullUsername = validTextMessage();
    nullUsername.setUsername(null);
    assertTrue(MessageValidator.validate(nullUsername).contains(expected));

    ClientMessage tooShort = validTextMessage();
    tooShort.setUsername("ab");
    assertTrue(MessageValidator.validate(tooShort).contains(expected));

    ClientMessage badChars = validTextMessage();
    badChars.setUsername("bad-name!");
    assertTrue(MessageValidator.validate(badChars).contains(expected));

    ClientMessage tooLong = validTextMessage();
    tooLong.setUsername("a".repeat(21));
    assertTrue(MessageValidator.validate(tooLong).contains(expected));
  }

  @Test
  void validate_textMessageEmptyOrTooLong_addsMessageLengthError() {
    String expected = "message must be 1-500 characters for TEXT messages";

    ClientMessage empty = validTextMessage();
    empty.setMessage("");
    assertTrue(MessageValidator.validate(empty).contains(expected));

    ClientMessage tooLong = validTextMessage();
    tooLong.setMessage("a".repeat(501));
    assertTrue(MessageValidator.validate(tooLong).contains(expected));
  }

  @Test
  void validate_nonTextMessageType_doesNotEnforceMessageLengthRule() {
    ClientMessage msg = validTextMessage();
    msg.setMessageType(Messagetype.JOIN);
    msg.setMessage(null);

    assertFalse(MessageValidator.validate(msg)
        .contains("message must be 1-500 characters for TEXT messages"));
  }

  @Test
  void validate_timestampInvalid_addsTimestampError() {
    ClientMessage msg = validTextMessage();
    msg.setTimestamp("not-a-timestamp");

    assertTrue(MessageValidator.validate(msg).contains("timestamp must be in ISO-8601 format"));
  }

  @Test
  void validate_messageTypeNull_addsRequiredError() {
    ClientMessage msg = validTextMessage();
    msg.setMessageType(null);

    assertTrue(MessageValidator.validate(msg).contains("messageType is required"));
  }

  @Test
  void validate_roomIdNull_addsRoomIdError() {
    ClientMessage msg = validTextMessage();
    msg.setRoomId(null);

    assertTrue(MessageValidator.validate(msg).contains("roomId must be numeric"));
  }

  @Test
  void validate_roomIdOutOfRange_addsRoomIdError() {
    String expected = "roomId must be between 1 and 20";

    ClientMessage zero = validTextMessage();
    zero.setRoomId("0");
    assertTrue(MessageValidator.validate(zero).contains(expected));

    ClientMessage tooBig = validTextMessage();
    tooBig.setRoomId("21");
    assertTrue(MessageValidator.validate(tooBig).contains(expected));
  }

  @Test
  void validate_roomIdNotNumeric_addsRoomIdError() {
    ClientMessage msg = validTextMessage();
    msg.setRoomId("abc");

    assertTrue(MessageValidator.validate(msg).contains("roomId must be numeric"));
  }
}