package dev.localllm.harness.adapter.ollama.toolcall;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import dev.localllm.harness.adapter.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.ToolSpec;

import static org.assertj.core.api.Assertions.assertThat;

class TextJsonToolCallModeTest {

  private static final List<ToolSpec> TOOLS = List.of(new ToolSpec("read_file",
      "Read a file.\nWith line numbers.", """
      {
        "type" : "object",
        "properties" : { "path" : { "type" : "string" } },
        "required" : [ "path" ]
      }""", ToolSource.BUILTIN, null));

  private final TextJsonToolCallMode mode = new TextJsonToolCallMode(
      Mappers.getMapper(ChatMessageSpringAiMapper.class), new ToolBlockParser());

  @Test
  void toMessages_WithToolHistory_RendersCallsAndResultsAsText() {
    // Arrange
    ToolCall call = new ToolCall("c1", "read_file", "{\"path\":\"Calc.java\"}");
    ToolCall broken = new ToolCall("c2", ToolCall.PARSE_ERROR, "{}");
    List<ChatMessage> history = List.of(
        new ChatMessage(Role.SYSTEM, "You are a coding assistant."),
        new ChatMessage(Role.USER, "What does add do?"),
        ChatMessage.assistant("Let me look.", List.of(call)),
        ChatMessage.toolResult(new ToolResult("c1", "read_file", "1\tclass Calc {}", false,
            false, Duration.ZERO)),
        ChatMessage.assistant("```tool\n{\"name\": }", List.of(broken)),
        ChatMessage.toolResult(ToolResult.rejected(broken, "TOOL_ERROR: invalid JSON.")));

    // Act
    List<Message> messages = mode.toMessages(
        new ChatRequest(LlmProvider.OLLAMA, "codellama:7b", history, false, TOOLS));

    // Assert
    assertThat(messages).extracting(Message::getMessageType).containsExactly(
        MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER,
        MessageType.ASSISTANT, MessageType.USER);
    assertThat(messages.getFirst().getText())
        .startsWith("You are a coding assistant.\n\n# Tools\n")
        .contains("- read_file: Read a file. With line numbers.\n  arguments (JSON Schema): "
            + "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},"
            + "\"required\":[\"path\"]}\n");
    assertThat(messages.get(2).getText()).isEqualTo(
        "Let me look.\n\n```tool\n{\"name\":\"read_file\",\"args\":{\"path\":\"Calc.java\"}}\n```");
    assertThat(messages.get(3).getText()).isEqualTo("TOOL_RESULT read_file:\n1\tclass Calc {}");
    assertThat(messages.get(4).getText()).isEqualTo("```tool\n{\"name\": }");
    assertThat(messages.get(5).getText()).isEqualTo("TOOL_ERROR: invalid JSON.");
  }

  @Test
  void toMessages_WithoutSystemMessage_AddsToolPromptFirst() {
    // Act
    List<Message> messages = mode.toMessages(new ChatRequest(LlmProvider.OLLAMA, "codellama:7b",
        List.of(new ChatMessage(Role.USER, "hi")), false, TOOLS));

    // Assert
    assertThat(messages).extracting(Message::getMessageType)
        .containsExactly(MessageType.SYSTEM, MessageType.USER);
    assertThat(messages.getFirst().getText()).startsWith("# Tools\n");
  }

  @Test
  void toMessages_WithoutTools_KeepsSystemPromptUnchanged() {
    // Act
    List<Message> messages = mode.toMessages(new ChatRequest(LlmProvider.OLLAMA, "codellama:7b",
        List.of(new ChatMessage(Role.SYSTEM, "Be brief.")), false, List.of()));

    // Assert
    assertThat(messages).singleElement()
        .satisfies(message -> assertThat(message.getText()).isEqualTo("Be brief."));
    assertThat(mode.toolCallbacks(TOOLS)).isEmpty();
  }

  @Test
  void collector_BlockSplitAcrossFragments_IsHiddenAndClosesTheAnswer() {
    // Arrange
    ToolCallCollector collector = mode.newCollector(TOOLS);
    List<String> fragments = List.of("Use `x` and ", "reading.\n`", "``to", "ol\n{\"name\": ",
        "\"read_file\", \"args\": {\"path\": \"A.java\"}}\n``", "`", "\nTOOL_RESULT invented");
    StringBuilder shown = new StringBuilder();
    StringBuilder content = new StringBuilder();
    boolean completeBeforeFence = false;

    // Act
    for (String fragment : fragments) {
      completeBeforeFence |= collector.isComplete();
      content.append(fragment);
      shown.append(collector.visible(fragment));
      if (collector.isComplete()) {
        break;
      }
    }
    shown.append(collector.flush());
    CollectedToolCalls collected = collector.finish(content.toString());

    // Assert
    assertThat(completeBeforeFence).isFalse();
    assertThat(collector.isComplete()).isTrue();
    assertThat(shown.toString()).isEqualTo("Use `x` and reading.\n");
    assertThat(collected.content()).isEqualTo("Use `x` and reading.");
    assertThat(collected.calls()).singleElement().satisfies(call ->
        assertThat(call.argumentsJson()).isEqualTo("{\"path\":\"A.java\"}"));
    assertThat(collected.error()).isNull();
  }

  @Test
  void collector_TextEndingLikeAFence_IsReleasedByFlush() {
    // Arrange
    ToolCallCollector collector = mode.newCollector(TOOLS);

    // Act
    String shown = collector.visible("Done ``");
    String flushed = collector.flush();
    CollectedToolCalls collected = collector.finish("Done ``");

    // Assert
    assertThat(shown).isEqualTo("Done ");
    assertThat(flushed).isEqualTo("``");
    assertThat(collected).isEqualTo(new CollectedToolCalls("Done ``", List.of(), null));
  }

  @Test
  void collector_InvalidBlock_ReturnsErrorAndRawText() {
    // Arrange
    ToolCallCollector collector = mode.newCollector(TOOLS);
    String content = "```tool\n{\"name\": \"rm\"}\n```";

    // Act
    collector.visible(content);
    CollectedToolCalls collected = collector.finish(content);

    // Assert
    assertThat(collected.calls()).isEmpty();
    assertThat(collected.content()).isEqualTo(content);
    assertThat(collected.error()).isEqualTo("unknown tool rm; available tools: read_file");
  }

  @Test
  void collector_WithoutTools_PassesTextThrough() {
    // Arrange
    ToolCallCollector collector = mode.newCollector(List.of());
    String content = "```tool\n{\"name\": \"read_file\"}\n```";

    // Act
    String shown = collector.visible(content);
    CollectedToolCalls collected = collector.finish(content);

    // Assert
    assertThat(shown).isEqualTo(content);
    assertThat(collector.isComplete()).isFalse();
    assertThat(collected).isEqualTo(new CollectedToolCalls(content, List.of(), null));
  }
}
