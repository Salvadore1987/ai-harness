package dev.localllm.harness.application.service;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.RunAgentTurnCommand;
import dev.localllm.harness.application.dto.query.ListToolsCriteria;
import dev.localllm.harness.application.dto.result.ToolSummaryResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.exception.AgentLimitExceededException;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.FakeSessionCatalogPort;
import dev.localllm.harness.application.fixture.FakeToolExecutorPort;
import dev.localllm.harness.application.fixture.FixedContextBudgetPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.fixture.RecordingTurnOutputPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.AgentPolicy;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.ToolCall;
import dev.localllm.harness.application.model.ToolSource;
import dev.localllm.harness.application.model.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopServiceTest {

  private static final String MODEL = "qwen3.5:27b";
  private static final Path CWD = Path.of("/work/project");
  private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
  private static final ContextPolicy CONTEXT = new ContextPolicy(0.8, 0.25, 4.0);
  private static final AgentPolicy AGENT = new AgentPolicy(4, 2);

  private final TurnApplicationMapper mapper = Mappers.getMapper(TurnApplicationMapper.class);
  private final SessionId sessionId = SessionId.newId();
  private final InMemoryConversationPort conversation = new InMemoryConversationPort();
  private final FakeChatModelPort chatModel = new FakeChatModelPort();
  private final FakeToolExecutorPort tools = new FakeToolExecutorPort("read_file", "glob");
  private final RecordingTurnOutputPort output = new RecordingTurnOutputPort();
  private final FakeSessionCatalogPort catalog = new FakeSessionCatalogPort();
  private final FixedContextBudgetPort budget = new FixedContextBudgetPort(65536);
  private final TurnCancellation cancellation = new TurnCancellation();
  private AgentLoopService service;

  @BeforeEach
  void setUp() {
    ContextMeter meter = new ContextMeter(budget, CONTEXT);
    service = new AgentLoopService(conversation,
        new SessionRegistrar(catalog, Clock.fixed(NOW, ZoneOffset.UTC)), chatModel, tools,
        output, new ConversationCompactor(conversation, chatModel, meter), cancellation, AGENT,
        mapper);
  }

  @Test
  void execute_WithoutToolCalls_StoresAnswerAndOffersTools() {
    // Arrange
    chatModel.thenAnswer(new ChatChunk.Content("Hello"));

    // Act
    TurnResult result = service.execute(command("Hi"));

    // Assert
    assertThat(result.content()).isEqualTo("Hello");
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(conversation.history(sessionId)).containsExactly(
        new ChatMessage(Role.USER, "Hi"), ChatMessage.assistant("Hello", List.of()));
    ChatRequest request = chatModel.requests().getFirst();
    assertThat(request.tools()).extracting("name").containsExactly("read_file", "glob");
    assertThat(request.messages().getFirst().role()).isEqualTo(Role.SYSTEM);
    assertThat(request.messages().getFirst().content()).endsWith("Working directory: " + CWD);
    assertThat(tools.executed()).isEmpty();
    assertThat(catalog.find(sessionId)).isPresent();
  }

  @Test
  void execute_WithChainOfCalls_ExecutesEachAndReturnsResultsToModel() {
    // Arrange
    ToolCall first = new ToolCall("c1", "glob", "{\"pattern\":\"**/*.java\"}");
    ToolCall second = new ToolCall("c2", "read_file", "{\"path\":\"Calc.java\"}");
    chatModel.thenCall(first).thenCall(second).thenAnswer(new ChatChunk.Content("add sums"));

    // Act
    TurnResult result = service.execute(command("What does add do?"));

    // Assert
    assertThat(result.content()).isEqualTo("add sums");
    assertThat(tools.executed()).containsExactly(first, second);
    assertThat(tools.workingDirectories()).containsOnly(CWD);
    assertThat(conversation.history(sessionId)).extracting(ChatMessage::role).containsExactly(
        Role.USER, Role.ASSISTANT, Role.TOOL_RESULT, Role.ASSISTANT, Role.TOOL_RESULT,
        Role.ASSISTANT);
    ChatMessage firstResult = conversation.history(sessionId).get(2);
    assertThat(firstResult.toolCallId()).isEqualTo("c1");
    assertThat(firstResult.content()).isEqualTo("glob {\"pattern\":\"**/*.java\"}");
    assertThat(chatModel.requests()).hasSize(3);
    assertThat(chatModel.requests().getLast().messages()).extracting(ChatMessage::role)
        .containsExactly(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.TOOL_RESULT,
            Role.ASSISTANT, Role.TOOL_RESULT);
    assertThat(output.toolCalls()).containsExactly(first, second);
    assertThat(output.toolResults()).hasSize(2);
    assertThat(output.results()).containsExactly(result);
  }

  @Test
  void execute_WithSeveralSteps_SumsGeneratedTokensAndKeepsLastPromptSize() {
    // Arrange
    chatModel.reportUsage(new TokenUsage(100, 10))
        .thenCall(new ToolCall("c1", "glob", "{}"))
        .thenAnswer(new ChatChunk.Content("done"));

    // Act
    TurnResult result = service.execute(command("go"));

    // Assert
    assertThat(result.promptTokens()).isEqualTo(100);
    assertThat(result.completionTokens()).isEqualTo(20);
  }

  @Test
  void execute_WhenModelNeverAnswers_StopsAtIterationLimit() {
    // Arrange
    for (int i = 0; i < AGENT.maxIterations(); i++) {
      chatModel.thenCall(new ToolCall("c" + i, "glob", "{}"));
    }

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command("loop")))
        .isInstanceOfSatisfying(AgentLimitExceededException.class, e -> {
          assertThat(e.getLimit()).isEqualTo(AgentLimitExceededException.Limit.ITERATIONS);
          assertThat(e.getValue()).isEqualTo(4);
        });
    assertThat(tools.executed()).hasSize(4);
    assertThat(conversation.history(sessionId).getLast().role()).isEqualTo(Role.TOOL_RESULT);
  }

  @Test
  void execute_WithUnknownToolsInARow_StopsAtParseFailureLimit() {
    // Arrange
    chatModel.thenCall(new ToolCall("c1", "rm_rf", "{}"))
        .thenCall(new ToolCall("c2", "rm_rf", "{}"));

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command("delete")))
        .isInstanceOfSatisfying(AgentLimitExceededException.class, e ->
            assertThat(e.getLimit()).isEqualTo(AgentLimitExceededException.Limit.PARSE_FAILURES));
    assertThat(tools.executed()).isEmpty();
    assertThat(conversation.history(sessionId).get(2).content())
        .isEqualTo("Unknown tool rm_rf. Available tools: glob, read_file");
  }

  @Test
  void execute_WithUnreadableToolCall_ReturnsToolErrorAndContinues() {
    // Arrange
    chatModel.thenFailToParse("```tool\n{\"name\": }\n```", "invalid JSON")
        .thenAnswer(new ChatChunk.Content("fixed"));

    // Act
    TurnResult result = service.execute(command("go"));

    // Assert
    assertThat(result.content()).isEqualTo("fixed");
    assertThat(tools.executed()).isEmpty();
    List<ChatMessage> history = conversation.history(sessionId);
    assertThat(history.get(1).content()).isEqualTo("```tool\n{\"name\": }\n```");
    assertThat(history.get(1).toolCalls()).extracting(ToolCall::name)
        .containsExactly(ToolCall.PARSE_ERROR);
    assertThat(history.get(2).role()).isEqualTo(Role.TOOL_RESULT);
    assertThat(history.get(2).toolCallId()).isEqualTo(history.get(1).toolCalls().getFirst().id());
    assertThat(history.get(2).content())
        .isEqualTo("TOOL_ERROR: invalid JSON. Repeat the call in the correct format.");
    assertThat(output.toolResults()).singleElement()
        .satisfies(toolResult -> assertThat(toolResult.error()).isTrue());
  }

  @Test
  void execute_WithUnreadableToolCallsInARow_StopsAtParseFailureLimit() {
    // Arrange
    chatModel.thenFailToParse("```tool\n{", "invalid JSON")
        .thenFailToParse("```tool\n{}", "missing name");

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command("go")))
        .isInstanceOfSatisfying(AgentLimitExceededException.class, e ->
            assertThat(e.getLimit()).isEqualTo(AgentLimitExceededException.Limit.PARSE_FAILURES));
  }

  @Test
  void execute_WhenKnownToolFollowsUnknown_ResetsParseFailures() {
    // Arrange
    chatModel.thenCall(new ToolCall("c1", "rm_rf", "{}"))
        .thenCall(new ToolCall("c2", "glob", "{}"))
        .thenCall(new ToolCall("c3", "rm_rf", "{}"))
        .thenAnswer(new ChatChunk.Content("ok"));

    // Act
    TurnResult result = service.execute(command("go"));

    // Assert
    assertThat(result.content()).isEqualTo("ok");
  }

  @Test
  void execute_WhenCancelledDuringTool_SkipsRemainingCallsAndStops() {
    // Arrange
    tools.onExecute(cancellation::request);
    chatModel.thenCall(new ToolCall("c1", "glob", "{}"), new ToolCall("c2", "read_file", "{}"))
        .thenAnswer(new ChatChunk.Content("never"));

    // Act
    TurnResult result = service.execute(command("go"));

    // Assert
    assertThat(result.status()).isEqualTo(TurnStatus.CANCELLED);
    assertThat(tools.executed()).hasSize(1);
    assertThat(chatModel.requests()).hasSize(1);
    ChatMessage skipped = conversation.history(sessionId).getLast();
    assertThat(skipped.toolCallId()).isEqualTo("c2");
    assertThat(skipped.content()).isEqualTo("Cancelled by the user before execution");
  }

  @Test
  void execute_WhenStreamIsCancelled_DropsCallsAndStops() {
    // Arrange
    chatModel.endWith(TurnStatus.CANCELLED).thenCall(new ToolCall("c1", "glob", "{}"));

    // Act
    TurnResult result = service.execute(command("go"));

    // Assert
    assertThat(result.status()).isEqualTo(TurnStatus.CANCELLED);
    assertThat(tools.executed()).isEmpty();
    assertThat(conversation.history(sessionId).getLast())
        .isEqualTo(ChatMessage.assistant("\n[cancelled]", List.of()));
  }

  @Test
  void execute_AfterPreviousCancellation_StartsUncancelled() {
    // Arrange
    cancellation.request();
    chatModel.thenCall(new ToolCall("c1", "glob", "{}")).thenAnswer(new ChatChunk.Content("ok"));

    // Act
    TurnResult result = service.execute(command("go"));

    // Assert
    assertThat(result.status()).isEqualTo(TurnStatus.COMPLETED);
    assertThat(tools.executed()).hasSize(1);
  }

  @Test
  void execute_WhenModelFailsOnFirstStep_LeavesHistoryUnchanged() {
    // Arrange
    chatModel.failWith(new LlmUnavailableException("http://localhost:11434",
        new IllegalStateException("down")));

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command("go")))
        .isInstanceOf(LlmUnavailableException.class);
    assertThat(conversation.history(sessionId)).isEmpty();
  }

  @Test
  void execute_WithCompactedHistory_MergesSummaryIntoSystemPrompt() {
    // Arrange
    conversation.append(sessionId, new ChatMessage(Role.SYSTEM, "earlier summary"));
    chatModel.thenAnswer(new ChatChunk.Content("ok"));

    // Act
    service.execute(command("go"));

    // Assert
    List<ChatMessage> messages = chatModel.requests().getFirst().messages();
    assertThat(messages).extracting(ChatMessage::role).containsExactly(Role.SYSTEM, Role.USER);
    assertThat(messages.getFirst().content())
        .contains("Working directory: " + CWD)
        .endsWith("Summary of the earlier conversation:\nearlier summary");
  }

  @Test
  void execute_WithThinking_RecordsItPerStep() {
    // Arrange
    chatModel.thenAnswer(new ChatChunk.Thinking("hmm"), new ChatChunk.Content("ok"));

    // Act
    service.execute(command("go"));

    // Assert
    assertThat(output.thinking()).containsExactly("hmm");
    assertThat(conversation.thinking(sessionId)).containsExactly("hmm");
  }

  @Test
  void execute_WhenHistoryGrowsPastThreshold_CompactsBetweenSteps() {
    // Arrange
    budget.setContextLength(60);
    chatModel.thenCall(new ToolCall("c1", "glob", "{\"pattern\":\"" + "x".repeat(200) + "\"}"))
        .thenAnswer(new ChatChunk.Content("summary"))
        .thenAnswer(new ChatChunk.Content("ok"));

    // Act
    service.execute(command("go"));

    // Assert
    assertThat(output.compactions()).isNotEmpty();
  }

  @Test
  void listTools_WithPrefix_ReturnsMatchingTools() {
    // Arrange
    ListToolsQueryService query = new ListToolsQueryService(tools, mapper);

    // Act
    List<ToolSummaryResult> result = query.execute(new ListToolsCriteria("re"));

    // Assert
    assertThat(result).containsExactly(
        new ToolSummaryResult("read_file", "Fake read_file", ToolSource.BUILTIN, null));
  }

  private RunAgentTurnCommand command(String text) {
    return new RunAgentTurnCommand(sessionId, CWD, LlmProvider.OLLAMA, MODEL, text, true);
  }
}
