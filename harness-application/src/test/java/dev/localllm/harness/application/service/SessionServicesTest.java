package dev.localllm.harness.application.service;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;
import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.result.SessionResult;
import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ChatMessage;
import dev.localllm.harness.application.model.Role;
import dev.localllm.harness.application.model.SessionId;

import static org.assertj.core.api.Assertions.assertThat;

class SessionServicesTest {

  private final TurnApplicationMapper mapper = Mappers.getMapper(TurnApplicationMapper.class);
  private final InMemoryConversationPort conversation = new InMemoryConversationPort();

  @Test
  void startSession_WithPreviousSession_ClearsItsHistoryAndReturnsNewId() {
    // Arrange
    SessionId previous = SessionId.newId();
    conversation.append(previous, new ChatMessage(Role.USER, "old"));
    StartSessionService service = new StartSessionService(conversation, mapper);

    // Act
    SessionResult result = service.execute(new StartSessionCommand(previous));

    // Assert
    assertThat(conversation.history(previous)).isEmpty();
    assertThat(result.sessionId()).isNotEqualTo(previous);
  }

  @Test
  void startSession_OnStartup_ReturnsTimeOrderedId() {
    // Arrange
    StartSessionService service = new StartSessionService(conversation, mapper);

    // Act
    SessionResult result = service.execute(new StartSessionCommand(null));

    // Assert
    assertThat(result.sessionId().value().version()).isEqualTo(7);
  }

  @Test
  void cancelTurn_Always_CancelsActiveStream() {
    // Arrange
    FakeChatModelPort chatModel = new FakeChatModelPort();
    CancelTurnService service = new CancelTurnService(chatModel);

    // Act
    service.execute(new CancelTurnCommand(SessionId.newId()));

    // Assert
    assertThat(chatModel.cancelCount()).isEqualTo(1);
  }
}
