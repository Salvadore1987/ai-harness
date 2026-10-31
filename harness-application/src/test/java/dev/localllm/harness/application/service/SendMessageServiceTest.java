package dev.localllm.harness.application.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.FakeSessionCatalogPort;
import dev.localllm.harness.application.fixture.FixedContextBudgetPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.fixture.RecordingTurnOutputPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class SendMessageServiceTest {

  private static final String MODEL = "qwen3.5:27b";
  private static final Path CWD = Path.of("/work/project");
  private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
  private static final ContextPolicy POLICY = new ContextPolicy(0.8, 0.25, 4.0);

  private final SessionId sessionId = SessionId.newId();
  private final InMemoryConversationPort conversation = new InMemoryConversationPort();
  private final FakeChatModelPort chatModel = new FakeChatModelPort();
  private final RecordingTurnOutputPort output = new RecordingTurnOutputPort();
  private final FakeSessionCatalogPort catalog = new FakeSessionCatalogPort();
  private final FixedContextBudgetPort budget = new FixedContextBudgetPort(65536);
  private SendMessageService service;

  @BeforeEach
  void setUp() {
    ContextMeter meter = new ContextMeter(budget, POLICY);
    service = new SendMessageService(conversation,
        new SessionRegistrar(catalog, Clock.fixed(NOW, ZoneOffset.UTC)), chatModel, output,
        new ConversationCompactor(conversation, chatModel, meter),
        Mappers.getMapper(TurnApplicationMapper.class));
  }

  @Test
  void execute_WithAnswer_StoresUserAndAssistantMessages() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("Hello"), new ChatChunk.Content(" there"));

    // Act
    service.execute(command("Hi", true));

    // Assert
    assertThat(conversation.history(sessionId)).containsExactly(
        new ChatMessage(Role.USER, "Hi"),
        new ChatMessage(Role.ASSISTANT, "Hello there"));
  }

  @Test
  void execute_WithThinkingChunks_PresentsThinkingButDoesNotStoreIt() {
    // Arrange
    chatModel.respondWith(
        new ChatChunk.Thinking("Let me think"),
        new ChatChunk.Content("42"));

    // Act
    service.execute(command("Answer?", true));

    // Assert
    assertThat(output.thinking()).containsExactly("Let me think");
    assertThat(output.content()).containsExactly("42");
    assertThat(conversation.history(sessionId))
        .extracting(ChatMessage::content)
        .noneMatch(text -> text.contains("Let me think"));
  }

  @Test
  void execute_WithPreviousHistory_SendsHistoryPlusNewMessage() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.USER, "first"));
    conversation.append(sessionId, new ChatMessage(Role.ASSISTANT, "reply"));
    chatModel.respondWith(new ChatChunk.Content("ok"));

    // Act
    service.execute(command("second", false));

    // Assert
    ChatRequest request = chatModel.requests().getFirst();
    assertThat(request.model()).isEqualTo(MODEL);
    assertThat(request.thinkingEnabled()).isFalse();
    assertThat(request.messages()).extracting(ChatMessage::content)
        .containsExactly("first", "reply", "second");
  }

  @Test
  void execute_WhenCancelled_StoresPartialAnswerWithMarker() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("partial")).endWith(TurnStatus.CANCELLED);

    // Act
    TurnResult result = service.execute(command("long question", true));

    // Assert
    assertThat(result.status()).isEqualTo(TurnStatus.CANCELLED);
    assertThat(conversation.history(sessionId).getLast().content())
        .isEqualTo("partial\n[cancelled]");
  }

  @Test
  void execute_WhenInterrupted_StoresPartialAnswerWithMarker() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("half")).endWith(TurnStatus.INTERRUPTED);

    // Act
    service.execute(command("question", true));

    // Assert
    assertThat(conversation.history(sessionId).getLast().content())
        .isEqualTo("half\n[interrupted]");
  }

  @Test
  void execute_WhenModelFails_LeavesHistoryUnchanged() {
    // Arrange
    chatModel.failWith(new LlmUnavailableException("http://localhost:11434",
        new IllegalStateException("refused")));

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command("Hi", true)))
        .isInstanceOf(LlmUnavailableException.class);
    assertThat(conversation.history(sessionId)).isEmpty();
    assertThat(output.results()).isEmpty();
  }

  @Test
  void execute_WithUsage_ReturnsTokenCountersAndCompletesOutput() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("done")).reportUsage(new TokenUsage(100, 20));

    // Act
    TurnResult result = service.execute(command("count", true));

    // Assert
    assertThat(result.content()).isEqualTo("done");
    assertThat(result.promptTokens()).isEqualTo(100);
    assertThat(result.completionTokens()).isEqualTo(20);
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(output.results()).isEqualTo(List.of(result));
  }

  @Test
  void execute_WithAnthropicSession_SendsRequestToAnthropic() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("ok"));
    SendMessageCommand command = new SendMessageCommand(
        sessionId, CWD, LlmProvider.ANTHROPIC, "claude-opus-5-5", "Hi", true);

    // Act
    service.execute(command);

    // Assert
    assertThat(chatModel.requests()).extracting(ChatRequest::provider, ChatRequest::model)
        .containsExactly(tuple(LlmProvider.ANTHROPIC, "claude-opus-5-5"));
    assertThat(catalog.find(sessionId)).map(SessionInfo::provider)
        .contains(LlmProvider.ANTHROPIC);
  }

  @Test
  void execute_OnFirstTurn_RegistersSessionWithSingleLineTitle() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("ok"));

    // Act
    service.execute(command("  Explain\n  the build  ", true));

    // Assert
    assertThat(catalog.find(sessionId)).contains(
        new SessionInfo(sessionId, CWD, LlmProvider.OLLAMA, MODEL, "Explain the build", NOW, 0));
  }

  @Test
  void execute_OnLaterTurn_KeepsOriginalMetadata() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("ok"));
    service.execute(command("first question", true));

    // Act
    service.execute(command("second question", true));

    // Assert
    assertThat(catalog.find(sessionId)).hasValueSatisfying(
        info -> assertThat(info.title()).isEqualTo("first question"));
  }

  @Test
  void execute_WithThinking_RecordsItSeparatelyFromHistory() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Thinking("step 1, "), new ChatChunk.Thinking("step 2"),
        new ChatChunk.Content("done"));

    // Act
    service.execute(command("go", true));

    // Assert
    assertThat(conversation.thinking(sessionId)).containsExactly("step 1, step 2");
  }

  @Test
  void execute_WhenHistoryReachesThreshold_CompactsAndNotifies() {
    // Arrange
    budget.setContextLength(40);
    chatModel.respondWith(new ChatChunk.Content("a".repeat(60)));
    service.execute(command("q1", false));

    // Act
    service.execute(command("q2", false));

    // Assert
    assertThat(output.compactions()).isNotEmpty();
    assertThat(output.compactions().getLast().compacted()).isTrue();
    assertThat(conversation.history(sessionId).getFirst().role()).isEqualTo(Role.SYSTEM);
  }

  @Test
  void execute_BelowThreshold_DoesNotCompact() {
    // Arrange
    chatModel.respondWith(new ChatChunk.Content("short"));

    // Act
    service.execute(command("q", false));

    // Assert
    assertThat(output.compactions()).isEmpty();
    assertThat(conversation.history(sessionId)).hasSize(2);
  }

  private SendMessageCommand command(String text, boolean thinking) {
    return new SendMessageCommand(sessionId, CWD, LlmProvider.OLLAMA, MODEL, text, thinking);
  }
}
