package dev.localllm.harness.adapter.anthropic.mapper;

import java.time.Duration;
import java.util.List;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageParam;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageAnthropicMapperTest {

  private final ChatMessageAnthropicMapper mapper =
      Mappers.getMapper(ChatMessageAnthropicMapper.class);

  @Test
  void toSystemPrompt_WithSystemMessages_JoinsThemAndSkipsOthers() {
    // Arrange
    List<ChatMessage> history = List.of(new ChatMessage(Role.SYSTEM, "You are a harness"),
        new ChatMessage(Role.USER, "hi"), new ChatMessage(Role.SYSTEM, "Summary"));

    // Act
    String system = mapper.toSystemPrompt(history);

    // Assert
    assertThat(system).isEqualTo("You are a harness\n\nSummary");
  }

  @Test
  void toMessageParams_WithParallelToolResults_SendsThemInOneUserMessage() {
    // Arrange
    ToolCall first = new ToolCall("toolu_1", "read_file", "{\"path\":\"A.java\"}");
    ToolCall second = new ToolCall("toolu_2", "glob", "{}");
    List<ChatMessage> history = List.of(
        new ChatMessage(Role.SYSTEM, "system"),
        new ChatMessage(Role.USER, "look"),
        ChatMessage.assistant("Reading", List.of(first, second)),
        ChatMessage.toolResult(result(first, "class A")),
        ChatMessage.toolResult(result(second, "A.java")),
        new ChatMessage(Role.ASSISTANT, "Done"));

    // Act
    List<MessageParam> params = mapper.toMessageParams(history);

    // Assert
    assertThat(params).hasSize(4);
    assertThat(params.get(0).isUser()).isTrue();
    List<ContentBlockParam> assistant = blocks(params.get(1));
    assertThat(assistant).hasSize(3);
    assertThat(assistant.get(0).asText().text()).isEqualTo("Reading");
    assertThat(assistant.get(1).asToolUse().id()).isEqualTo("toolu_1");
    assertThat(assistant.get(1).asToolUse().name()).isEqualTo("read_file");
    assertThat(assistant.get(1).asToolUse().input()._additionalProperties().get("path"))
        .hasToString("A.java");
    List<ContentBlockParam> results = blocks(params.get(2));
    assertThat(results).extracting(block -> block.asToolResult().toolUseId())
        .containsExactly("toolu_1", "toolu_2");
    assertThat(params.get(3).isAssistant()).isTrue();
  }

  @Test
  void toMessageParams_WithUnreadableToolCall_SendsItsErrorAsText() {
    // Arrange
    ToolCall unreadable = ToolCall.parseError();
    List<ChatMessage> history = List.of(
        new ChatMessage(Role.USER, "look"),
        ChatMessage.assistant("", List.of(unreadable)),
        ChatMessage.toolResult(result(unreadable, "TOOL_ERROR: bad input")));

    // Act
    List<MessageParam> params = mapper.toMessageParams(history);

    // Assert
    assertThat(params).hasSize(1);
    assertThat(blocks(params.get(0))).extracting(block -> block.asText().text())
        .containsExactly("look", "TOOL_ERROR: bad input");
  }

  @Test
  void toMessageParams_WithBlankAssistantMessage_LeavesItOutAndMergesNeighbours() {
    // Arrange
    List<ChatMessage> history = List.of(
        new ChatMessage(Role.USER, "first"),
        new ChatMessage(Role.ASSISTANT, "  "),
        new ChatMessage(Role.USER, "second"));

    // Act
    List<MessageParam> params = mapper.toMessageParams(history);

    // Assert
    assertThat(params).hasSize(1);
    assertThat(blocks(params.get(0))).hasSize(2);
  }

  private static ToolResult result(ToolCall call, String output) {
    return new ToolResult(call.id(), call.name(), output, false, false, Duration.ZERO);
  }

  private static List<ContentBlockParam> blocks(MessageParam param) {
    return param.isUser()
        ? param.asUser().content().asContentBlockParams()
        : param.asAssistant().content().asContentBlockParams();
  }
}
