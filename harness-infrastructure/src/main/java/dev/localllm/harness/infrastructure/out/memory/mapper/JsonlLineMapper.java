package dev.localllm.harness.infrastructure.out.memory.mapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.infrastructure.out.memory.dto.JsonlLine;
import dev.localllm.harness.infrastructure.out.memory.dto.JsonlToolCall;

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
  @Mapping(target = "toolCalls", ignore = true)
  @Mapping(target = "callId", ignore = true)
  @Mapping(target = "toolName", ignore = true)
  JsonlLine toMetaLine(SessionInfo info);

  /**
   * Restores session metadata from its meta line. The meta line does not store the provider
   * yet, so every stored session is an Ollama session.
   *
   * @param line meta line
   * @param messageCount number of message lines in the file
   * @return session metadata
   */
  @Mapping(target = "workingDirectory", source = "line.cwd")
  @Mapping(target = "provider", constant = "OLLAMA")
  @Mapping(target = "messageCount", source = "messageCount")
  SessionInfo toSessionInfo(JsonlLine line, int messageCount);

  /**
   * Builds the line of a conversation message. A tool response message is stored with its first
   * response; the harness writes one response per message.
   *
   * @param message Spring AI message
   * @param createdAt time of writing
   * @return message line
   */
  default JsonlLine toMessageLine(Message message, Instant createdAt) {
    String text = Objects.requireNonNullElse(message.getText(), "");
    if (message instanceof ToolResponseMessage tool && !tool.getResponses().isEmpty()) {
      ToolResponseMessage.ToolResponse response = tool.getResponses().getFirst();
      return new JsonlLine(JsonlLine.MESSAGE, null, null, null, null, JsonlLine.TOOL_RESULT_ROLE,
          response.responseData(), null, response.id(), response.name(), null, null, createdAt);
    }
    List<JsonlToolCall> toolCalls = message instanceof AssistantMessage assistant
        && assistant.hasToolCalls() ? toJsonlToolCalls(assistant.getToolCalls()) : null;
    return new JsonlLine(JsonlLine.MESSAGE, null, null, null, null,
        message.getMessageType().name(), text, toolCalls, null, null, null, null, createdAt);
  }

  /**
   * Restores a Spring AI message from its line.
   *
   * @param line message line
   * @return Spring AI message
   */
  default Message toMessage(JsonlLine line) {
    String content = Objects.requireNonNullElse(line.content(), "");
    String role = Objects.requireNonNull(line.role(), "role");
    if (JsonlLine.TOOL_RESULT_ROLE.equals(role) || MessageType.TOOL.name().equals(role)) {
      return ToolResponseMessage.builder()
          .responses(List.of(new ToolResponseMessage.ToolResponse(
              Objects.requireNonNullElse(line.callId(), ""),
              Objects.requireNonNullElse(line.toolName(), ""),
              content)))
          .build();
    }
    return switch (MessageType.valueOf(role)) {
      case SYSTEM -> new SystemMessage(content);
      case ASSISTANT -> AssistantMessage.builder()
          .content(content)
          .toolCalls(toSpringAiToolCalls(Objects.requireNonNullElse(line.toolCalls(), List.of())))
          .build();
      default -> new UserMessage(content);
    };
  }

  /**
   * Converts the tool calls of an assistant message for storage.
   *
   * @param calls Spring AI tool calls
   * @return stored tool calls
   */
  List<JsonlToolCall> toJsonlToolCalls(List<AssistantMessage.ToolCall> calls);

  /**
   * Converts one tool call for storage.
   *
   * @param call Spring AI tool call
   * @return stored tool call
   */
  @Mapping(target = "argsJson", source = "arguments")
  JsonlToolCall toJsonlToolCall(AssistantMessage.ToolCall call);

  /**
   * Restores the tool calls of an assistant message.
   *
   * @param calls stored tool calls
   * @return Spring AI tool calls
   */
  List<AssistantMessage.ToolCall> toSpringAiToolCalls(List<JsonlToolCall> calls);

  /**
   * Restores one tool call.
   *
   * @param call stored tool call
   * @return Spring AI tool call
   */
  @Mapping(target = "type", constant = "function")
  @Mapping(target = "arguments", source = "argsJson")
  AssistantMessage.ToolCall toSpringAiToolCall(JsonlToolCall call);

  /**
   * Builds a compaction line.
   *
   * @param summary summary of the replaced messages, possibly empty
   * @param replacedCount number of leading messages replaced
   * @param createdAt time of writing
   * @return compaction line
   */
  default JsonlLine toCompactionLine(String summary, int replacedCount, Instant createdAt) {
    return new JsonlLine(JsonlLine.COMPACTION, null, null, null, null, null, null, null, null,
        null, summary, replacedCount, createdAt);
  }

  /**
   * Builds a reasoning line.
   *
   * @param thinking reasoning text of one turn
   * @param createdAt time of writing
   * @return thinking line
   */
  default JsonlLine toThinkingLine(String thinking, Instant createdAt) {
    return new JsonlLine(JsonlLine.THINKING, null, null, null, null, null, thinking, null, null,
        null, null, null, createdAt);
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
