package dev.localllm.harness.application.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.CompactSessionCommand;
import dev.localllm.harness.application.dto.query.ContextUsageCriteria;
import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.ContextUsageResult;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.FixedContextBudgetPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.CompactionOutcome;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.ContextUsage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolResult;
import dev.localllm.harness.application.model.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ContextServicesTest {

  private static final LlmProvider PROVIDER = LlmProvider.OLLAMA;
  private static final String MODEL = "qwen3.5:27b";
  private static final ContextPolicy POLICY = new ContextPolicy(0.8, 0.01, 4.0);

  private final TurnApplicationMapper mapper = Mappers.getMapper(TurnApplicationMapper.class);
  private final SessionId sessionId = SessionId.newId();
  private final InMemoryConversationPort conversation = new InMemoryConversationPort();
  private final FakeChatModelPort chatModel = new FakeChatModelPort();
  private final FixedContextBudgetPort budget = new FixedContextBudgetPort(1000);
  private final ContextMeter meter = new ContextMeter(budget, POLICY);
  private final ConversationCompactor compactor =
      new ConversationCompactor(conversation, chatModel, meter);

  @Test
  void estimateTokens_WithMessages_DividesCharsAndAddsOverheadPerMessage() {
    // Arrange
    List<ChatMessage> messages = List.of(
        new ChatMessage(Role.USER, "a".repeat(40)), new ChatMessage(Role.ASSISTANT, "b".repeat(2)));

    // Act
    int tokens = meter.estimateTokens(messages);

    // Assert
    assertThat(tokens).isEqualTo(11 + 2 * 4);
  }

  @Test
  void measure_WithBudget_ReportsPercentAndThreshold() {
    // Arrange
    List<ChatMessage> messages = List.of(new ChatMessage(Role.USER, "a".repeat(3184)));

    // Act
    ContextUsage usage = meter.measure(messages, PROVIDER, MODEL);

    // Assert
    assertThat(usage).isEqualTo(new ContextUsage(800, 1000, 80));
    assertThat(meter.isOverThreshold(usage)).isTrue();
    assertThat(meter.isOverThreshold(new ContextUsage(799, 1000, 79))).isFalse();
  }

  @Test
  void compact_WithLongHistory_SummarizesOlderMessagesAndKeepsLastOnes() {
    // Arrange
    seed("q1", "a1", "q2", "a2", "q3", "a3");
    chatModel.respondWith(new ChatChunk.Content("  summary of q1-q2  "));

    // Act
    CompactionOutcome outcome = compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    assertThat(conversation.history(sessionId)).containsExactly(
        new ChatMessage(Role.SYSTEM, "summary of q1-q2"),
        new ChatMessage(Role.USER, "q3"),
        new ChatMessage(Role.ASSISTANT, "a3"));
    assertThat(outcome.compacted()).isTrue();
    assertThat(outcome.summarized()).isTrue();
    assertThat(outcome.tokensAfter()).isLessThan(outcome.tokensBefore());
  }

  @Test
  void compact_SummaryRequest_SendsTranscriptWithoutThinking() {
    // Arrange
    seed("q1", "a1", "q2", "a2");
    chatModel.respondWith(new ChatChunk.Content("s"));

    // Act
    compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    ChatRequest request = chatModel.requests().getFirst();
    assertThat(request.thinkingEnabled()).isFalse();
    assertThat(request.messages().getFirst().role()).isEqualTo(Role.SYSTEM);
    assertThat(request.messages().getLast().content()).isEqualTo("user: q1\n\nassistant: a1");
  }

  @Test
  void compact_WhenModelFails_DropsOlderMessagesWithoutSummary() {
    // Arrange
    seed("q1", "a1", "q2", "a2");
    chatModel.failWith(new LlmUnavailableException("http://localhost:11434",
        new IllegalStateException("down")));

    // Act
    CompactionOutcome outcome = compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    assertThat(outcome.summarized()).isFalse();
    assertThat(conversation.history(sessionId)).extracting(ChatMessage::content)
        .containsExactly("q2", "a2");
  }

  @Test
  void compact_WhenSummaryIsCancelled_DropsOlderMessagesWithoutSummary() {
    // Arrange
    seed("q1", "a1", "q2", "a2");
    chatModel.respondWith(new ChatChunk.Content("part")).endWith(TurnStatus.CANCELLED);

    // Act
    CompactionOutcome outcome = compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    assertThat(outcome.summarized()).isFalse();
    assertThat(conversation.history(sessionId)).hasSize(2);
  }

  @Test
  void compact_WithShortHistory_ChangesNothing() {
    // Arrange
    seed("q1", "a1");

    // Act
    CompactionResult result = new CompactSessionService(compactor, mapper)
        .execute(new CompactSessionCommand(sessionId, PROVIDER, MODEL));

    // Assert
    assertThat(result.compacted()).isFalse();
    assertThat(chatModel.requests()).isEmpty();
  }

  @Test
  void compact_WithLargeOldAttachment_SendsFileReferenceInsteadOfContent() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER,
        "look at @Calc.java\n\n<file path=\"Calc.java\">\n" + "x".repeat(2000) + "\n</file>"));
    seed("a1", "q2", "a2");
    chatModel.respondWith(new ChatChunk.Content("s"));

    // Act
    CompactionOutcome outcome = compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    String transcript = chatModel.requests().getFirst().messages().getLast().content();
    assertThat(transcript).startsWith("user: look at @Calc.java\n\n@Calc.java")
        .doesNotContain("xxx");
    assertThat(outcome.tokensAfter()).isLessThan(outcome.tokensBefore() / 10);
  }

  @Test
  void compact_WithLongLastMessage_KeepsNothingVerbatim() {
    // Arrange
    seed("q1", "a1", "q2", "a".repeat(400));
    chatModel.respondWith(new ChatChunk.Content("s"));

    // Act
    compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    assertThat(conversation.history(sessionId))
        .containsExactly(new ChatMessage(Role.SYSTEM, "s"));
  }

  @Test
  void compact_WhenTailWouldStartWithToolResult_MovesResultToSummary() {
    // Arrange
    ToolCall call = new ToolCall("c1", "read_file", "{\"path\":\"A.java\"}");
    conversation.append(sessionId, new ChatMessage(Role.USER, "q".repeat(40)));
    conversation.append(sessionId, ChatMessage.assistant("a".repeat(40), List.of(call)));
    conversation.append(sessionId, ChatMessage.toolResult(
        new ToolResult("c1", "read_file", "r", false, false, Duration.ZERO)));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, "d"));
    chatModel.respondWith(new ChatChunk.Content("s"));

    // Act
    compactor.compact(sessionId, PROVIDER, MODEL);

    // Assert
    assertThat(conversation.history(sessionId)).extracting(ChatMessage::role)
        .containsExactly(Role.SYSTEM, Role.ASSISTANT);
    assertThat(chatModel.requests().getFirst().messages().getLast().content())
        .contains("[called read_file {\"path\":\"A.java\"}]")
        .contains("tool_result read_file: r");
  }

  @Test
  void compactIfNeeded_BelowThreshold_ReturnsEmpty() {
    // Arrange
    seed("q1", "a1");

    // Act
    Optional<CompactionOutcome> outcome = compactor.compactIfNeeded(sessionId, PROVIDER, MODEL);

    // Assert
    assertThat(outcome).isEmpty();
  }

  @Test
  void contextUsage_ForSession_MeasuresItsHistory() {
    // Arrange
    seed("a".repeat(396));
    GetContextUsageQueryService service =
        new GetContextUsageQueryService(conversation, meter, mapper);

    // Act
    ContextUsageResult result =
        service.execute(new ContextUsageCriteria(sessionId, PROVIDER, MODEL));

    // Assert
    assertThat(result).isEqualTo(new ContextUsageResult(103, 1000, 10));
  }

  private void seed(String... contents) {
    for (int i = 0; i < contents.length; i++) {
      conversation.append(sessionId,
          new ChatMessage(i % 2 == 0 ? Role.USER : Role.ASSISTANT, contents[i]));
    }
  }
}
