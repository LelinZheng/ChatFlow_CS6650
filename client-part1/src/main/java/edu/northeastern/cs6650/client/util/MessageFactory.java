package edu.northeastern.cs6650.client.util;

import edu.northeastern.cs6650.client.model.ChatMessage;
import edu.northeastern.cs6650.client.model.MessageType;
import java.time.Instant;
import java.util.List;

public class MessageFactory {
  private static final Integer USER_ID_MIN = 1;
  private static final Integer USER_ID_MAX = 100000;

  private static final String USERNAME_PREFIX = "user";

  private static final List<String> MESSAGES = List.of(
      "Hello everyone!",
      "How's it going?",
      "Did you see the game last night?",
      "What's your favorite programming language?",
      "Anyone up for a chat?",
      "Just finished a great book!",
      "Any movie recommendations?",
      "It's a beautiful day outside.",
      "Working on a new project, excited to share soon!",
      "Coffee or tea?",

      "Good morning!",
      "Good afternoon!",
      "Good evening!",
      "Hope everyone is doing well.",
      "What are you working on today?",
      "Does anyone have tips for debugging?",
      "Just pushed some code to GitHub.",
      "Trying to optimize performance.",
      "Learning something new every day!",
      "This bug is driving me crazy.",

      "Anyone attending the next meetup?",
      "Just deployed my app!",
      "Waiting for tests to finish.",
      "Time for a break.",
      "Code reviews are important.",
      "Pair programming is fun.",
      "Reading documentation again.",
      "Refactoring old code.",
      "Writing unit tests.",
      "Running integration tests.",

      "Late night coding session.",
      "Early morning standup.",
      "Still waiting on feedback.",
      "Almost done with this feature.",
      "Trying out a new framework.",
      "This API is pretty neat.",
      "Backend or frontend?",
      "Full stack development is challenging.",
      "Scalability is key.",
      "Performance matters.",

      "Anyone working remotely today?",
      "Just joined the channel!",
      "Looking forward to the weekend.",
      "Does anyone have deployment tips?",
      "Running benchmarks right now.",
      "Trying to reduce latency.",
      "Monitoring system metrics.",
      "Scaling up the service.",
      "Investigating a production issue.",
      "Everything looks stable so far."
  );

  private static final Integer MESSAGE_TYPE_RANGE_START = 0;
  private static final Integer MESSAGE_TYPE_TEXT = 90; // 90% TEXT, 5% JOIN, 5% LEAVE
  private static final Integer MESSAGE_TYPE_JOIN = 95;
  private static final Integer MESSAGE_TYPE_LEAVE = 100;
  private static final int ROOM_ID_MIN = 1;
  private static final int ROOM_ID_MAX = 20;
  private final RandomGenerator randomGenerator = new RandomGenerator();

  public ChatMessage createMessage(){
    ChatMessage message = new ChatMessage();
    message.setMessageId(randomGenerator.generateRandomMessageId());
    message.setMessage(MESSAGES.get(randomGenerator
        .generateRandomInteger(0, MESSAGES.size()-1)));
    message.setUserId(String.valueOf(randomGenerator.generateRandomInteger
        (USER_ID_MIN,USER_ID_MAX)));
    message.setUsername(USERNAME_PREFIX + message.getUserId());
    message.setMessageType(determineMessageType());
    message.setTimestamp(Instant.now().toString());
    message.setRoomId(randomGenerator.generateRandomInteger(ROOM_ID_MIN, ROOM_ID_MAX));

    return message;
  }

  public ChatMessage createMessage(MessageType forcedType) {
    ChatMessage message = createMessage();
    message.setMessageType(forcedType);
    return message;
  }

  private MessageType determineMessageType() {
    int rand = randomGenerator.
        generateRandomInteger(MESSAGE_TYPE_RANGE_START, MESSAGE_TYPE_LEAVE - 1);
    if (rand < MESSAGE_TYPE_TEXT) {
      return MessageType.TEXT;
    } else if (rand < MESSAGE_TYPE_JOIN) {
      return MessageType.JOIN;
    } else {
      return MessageType.LEAVE;
    }
  }












}
