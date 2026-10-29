package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.result.SessionResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.port.in.StartSessionUseCase;

/**
 * Starts a new session; the previous one stays stored and can be resumed later.
 *
 * <p>Nothing is written until the first turn, so abandoned empty sessions leave no files.
 */
public class StartSessionService implements StartSessionUseCase {

  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param mapper converts the session identifier into the result
   */
  public StartSessionService(TurnApplicationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public SessionResult execute(StartSessionCommand command) {
    return mapper.toSessionResult(SessionId.newId());
  }
}
