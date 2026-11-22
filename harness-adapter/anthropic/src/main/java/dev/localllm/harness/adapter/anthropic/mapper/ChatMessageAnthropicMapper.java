package dev.localllm.harness.adapter.anthropic.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import org.mapstruct.Mapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.ToolCall;

/**
 * Converts the core conversation history into a Messages API request.
 *
 * <p>System messages go to the top-level {@code system} field. A tool result becomes a
 * {@code tool_result} block of a user message; consecutive messages of one side are merged into
 * one message, so that the results of parallel calls travel together, as the API expects. A
 * {@link ToolCall#PARSE_ERROR} call stands for a tool call the harness could not read: it has no
 * {@code tool_use} block, and its result is sent as plain text.
 */
@Mapper
public interface ChatMessageAnthropicMapper {

  /** Separator between the texts of several system messages. */
  String SYSTEM_SEPARATOR = "\n\n";

  /**
   * Joins the system messages of the history.
   *
   * @param messages core messages, oldest first
   * @return system prompt, empty if the history has none
   */
  default String toSystemPrompt(List<ChatMessage> messages) {
    return messages.stream()
        .filter(message -> message.role() == Role.SYSTEM)
        .map(ChatMessage::content)
        .filter(content -> !content.isBlank())
        .collect(Collectors.joining(SYSTEM_SEPARATOR));
  }

  /**
   * Converts the user, assistant and tool result messages, keeping the order.
   *
   * @param messages core messages, oldest first
   * @return alternating user and assistant messages; empty messages are left out
   */
  default List<MessageParam> toMessageParams(List<ChatMessage> messages) {
    List<MessageParam> params = new ArrayList<>();
    List<ContentBlockParam> blocks = new ArrayList<>();
    boolean assistant = false;
    for (ChatMessage message : messages) {
      if (message.role() == Role.SYSTEM) {
        continue;
      }
      List<ContentBlockParam> messageBlocks = toContentBlocks(message);
      if (messageBlocks.isEmpty()) {
        continue;
      }
      boolean messageFromAssistant = message.role() == Role.ASSISTANT;
      if (messageFromAssistant != assistant && !blocks.isEmpty()) {
        params.add(toMessageParam(assistant, blocks));
        blocks = new ArrayList<>();
      }
      assistant = messageFromAssistant;
      blocks.addAll(messageBlocks);
    }
    if (!blocks.isEmpty()) {
      params.add(toMessageParam(assistant, blocks));
    }
    return params;
  }

  /**
   * Converts one non-system message into its content blocks.
   *
   * @param message core message
   * @return blocks, empty for a message without text and tool calls
   */
  default List<ContentBlockParam> toContentBlocks(ChatMessage message) {
    List<ContentBlockParam> blocks = new ArrayList<>();
    switch (message.role()) {
      case SYSTEM -> { }
      case USER -> addText(blocks, message.content());
      case ASSISTANT -> {
        addText(blocks, message.content());
        for (ToolCall call : message.toolCalls()) {
          if (!ToolCall.PARSE_ERROR.equals(call.name())) {
            blocks.add(ContentBlockParam.ofToolUse(toToolUseBlock(call)));
          }
        }
      }
      case TOOL_RESULT -> {
        if (ToolCall.PARSE_ERROR.equals(message.toolName())) {
          addText(blocks, message.content());
        } else {
          blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
              .toolUseId(Objects.requireNonNullElse(message.toolCallId(), ""))
              .content(message.content())
              .build()));
        }
      }
    }
    return blocks;
  }

  /**
   * Converts a tool call of the history into a {@code tool_use} block.
   *
   * @param call core tool call
   * @return block with the arguments as a JSON object, empty if they are not one
   */
  default ToolUseBlockParam toToolUseBlock(ToolCall call) {
    return ToolUseBlockParam.builder()
        .id(call.id())
        .name(call.name())
        .input(ToolUseBlockParam.Input.builder()
            .additionalProperties(toJsonObject(call.argumentsJson()))
            .build())
        .build();
  }

  /**
   * Reads a JSON object into the values the SDK sends as they are.
   *
   * @param json JSON text
   * @return fields of the object, empty if the text is not a JSON object
   */
  default Map<String, JsonValue> toJsonObject(String json) {
    Map<String, JsonValue> fields = new LinkedHashMap<>();
    try {
      if (JsonMapper.shared().readValue(json, Object.class) instanceof Map<?, ?> object) {
        object.forEach((name, value) -> fields.put(String.valueOf(name), JsonValue.from(value)));
      }
    } catch (JacksonException e) {
      fields.clear();
    }
    return fields;
  }

  private static void addText(List<ContentBlockParam> blocks, String text) {
    // The API rejects text blocks that are empty or only whitespace.
    if (!text.isBlank()) {
      blocks.add(ContentBlockParam.ofText(text));
    }
  }

  private static MessageParam toMessageParam(boolean assistant, List<ContentBlockParam> blocks) {
    return assistant
        ? MessageParam.ofAssistantOfContentBlockParams(List.copyOf(blocks))
        : MessageParam.ofUserOfContentBlockParams(List.copyOf(blocks));
  }
}
