package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.result.SessionResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.out.ConversationPort;

/**
 * Starts a new session and discards the history of the replaced one.
 */
public class StartSessionService implements StartSessionUseCase {

  private final ConversationPort conversationPort;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param conversationPort conversation history storage
   * @param mapper converts the session identifier into the result
   */
  public StartSessionService(ConversationPort conversationPort, TurnApplicationMapper mapper) {
    this.conversationPort = conversationPort;
    this.mapper = mapper;
  }

  @Override
  public SessionResult execute(StartSessionCommand command) {
    SessionId previous = command.previousSessionId();
    if (previous != null) {
      conversationPort.clear(previous);
    }
    return mapper.toSessionResult(SessionId.newId());
  }
}
