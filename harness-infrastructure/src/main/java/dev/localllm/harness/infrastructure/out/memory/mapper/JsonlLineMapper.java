package dev.localllm.harness.infrastructure.out.memory.mapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.infrastructure.out.memory.dto.JsonlLine;

/**
 * Converts between session file lines, Spring AI messages and session metadata.
 */
@Mapper
public interface JsonlLineMapper {

  /**
   * Builds the metadata line of a session.
   *
   * @param info session metadata
   * @return meta line
   */
  @Mapping(target = "type", constant = JsonlLine.META)
  @Mapping(target = "cwd", source = "workingDirectory")
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "content", ignore = true)
  @Mapping(target = "summary", ignore = true)
  @Mapping(target = "replacedCount", ignore = true)
  JsonlLine toMetaLine(SessionInfo info);

  /**
   * Restores session metadata from its meta line.
   *
   * @param line meta line
   * @param messageCount number of message lines in the file
   * @return session metadata
   */
  @Mapping(target = "workingDirectory", source = "line.cwd")
  @Mapping(target = "messageCount", source = "messageCount")
  SessionInfo toSessionInfo(JsonlLine line, int messageCount);

  /**
   * Builds the line of a conversation message.
   *
   * @param message Spring AI message
   * @param createdAt time of writing
   * @return message line
   */
  default JsonlLine toMessageLine(Message message, Instant createdAt) {
    return new JsonlLine(JsonlLine.MESSAGE, null, null, null, null,
        message.getMessageType().name(), Objects.requireNonNullElse(message.getText(), ""),
        null, null, createdAt);
  }

  /**
   * Restores a Spring AI message from its line.
   *
   * @param line message line
   * @return Spring AI message
   */
  default Message toMessage(JsonlLine line) {
    String content = Objects.requireNonNullElse(line.content(), "");
    return switch (MessageType.valueOf(Objects.requireNonNull(line.role(), "role"))) {
      case SYSTEM -> new SystemMessage(content);
      case ASSISTANT -> new AssistantMessage(content);
      default -> new UserMessage(content);
    };
  }

  /**
   * Builds a compaction line.
   *
   * @param summary summary of the replaced messages, possibly empty
   * @param replacedCount number of leading messages replaced
   * @param createdAt time of writing
   * @return compaction line
   */
  default JsonlLine toCompactionLine(String summary, int replacedCount, Instant createdAt) {
    return new JsonlLine(JsonlLine.COMPACTION, null, null, null, null, null, null,
        summary, replacedCount, createdAt);
  }

  /**
   * Builds a reasoning line.
   *
   * @param thinking reasoning text of one turn
   * @param createdAt time of writing
   * @return thinking line
   */
  default JsonlLine toThinkingLine(String thinking, Instant createdAt) {
    return new JsonlLine(JsonlLine.THINKING, null, null, null, null, null, thinking,
        null, null, createdAt);
  }

  /**
   * Writes a session identifier as text.
   *
   * @param sessionId identifier
   * @return UUID text
   */
  default String toText(SessionId sessionId) {
    return sessionId.toString();
  }

  /**
   * Reads a session identifier from text.
   *
   * @param value UUID text
   * @return identifier
   */
  default SessionId toSessionId(String value) {
    return new SessionId(UUID.fromString(value));
  }

  /**
   * Writes a path as text.
   *
   * @param path path
   * @return path text
   */
  default String toText(Path path) {
    return path.toString();
  }

  /**
   * Reads a path from text.
   *
   * @param value path text
   * @return path
   */
  default Path toPath(String value) {
    return Path.of(value);
  }
}
