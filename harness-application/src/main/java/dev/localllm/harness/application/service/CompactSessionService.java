package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.CompactSessionCommand;
import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.port.in.CompactSessionUseCase;

/**
 * Compacts the history of a session on user request.
 */
public class CompactSessionService implements CompactSessionUseCase {

  private final ConversationCompactor compactor;
  private final TurnApplicationMapper mapper;

  /**
   * Creates the service.
   *
   * @param compactor performs the compaction
   * @param mapper converts the outcome into the result
   */
  public CompactSessionService(ConversationCompactor compactor, TurnApplicationMapper mapper) {
    this.compactor = compactor;
    this.mapper = mapper;
  }

  @Override
  public CompactionResult execute(CompactSessionCommand command) {
    return mapper.toCompactionResult(compactor.compact(command.sessionId(), command.model()));
  }
}
