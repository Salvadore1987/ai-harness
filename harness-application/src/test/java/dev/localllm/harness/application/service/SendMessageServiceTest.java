package dev.localllm.harness.application.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.fixture.RecordingTurnOutputPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatChunk;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.ChatRequest;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.TokenUsage;
import dev.localllm.harness.application.model.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SendMessageServiceTest {

  private static final String MODEL = "qwen3.5:27b";

  private final SessionId sessionId = SessionId.newId();
  private final InMemoryConversationPort conversation = new InMemoryConversationPort();
  private final FakeChatModelPort chatModel = new FakeChatModelPort();
  private final RecordingTurnOutputPort output = new RecordingTurnOutputPort();
  private SendMessageService service;

  @BeforeEach
  void setUp() {
    service = new SendMessageService(
        conversation, chatModel, output, Mappers.getMapper(TurnApplicationMapper.class));
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

  private SendMessageCommand command(String text, boolean thinking) {
    return new SendMessageCommand(sessionId, MODEL, text, thinking);
  }
}
