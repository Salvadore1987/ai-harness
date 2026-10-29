package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;
import dev.localllm.harness.application.port.in.CancelTurnUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;

/**
 * Cancels the turn that is currently running: the active model stream stops at once, the agent
 * loop stops before its next step.
 */
public class CancelTurnService implements CancelTurnUseCase {

  private final ChatModelPort chatModelPort;
  private final TurnCancellation cancellation;

  /**
   * Creates the service.
   *
   * @param chatModelPort language model whose active stream is cancelled
   * @param cancellation cancellation request checked by the agent loop
   */
  public CancelTurnService(ChatModelPort chatModelPort, TurnCancellation cancellation) {
    this.chatModelPort = chatModelPort;
    this.cancellation = cancellation;
  }

  @Override
  public void execute(CancelTurnCommand command) {
    cancellation.request();
    chatModelPort.cancelActive();
  }
}
