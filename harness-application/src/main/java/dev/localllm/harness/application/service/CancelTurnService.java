package dev.localllm.harness.application.service;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;
import dev.localllm.harness.application.port.in.CancelTurnUseCase;
import dev.localllm.harness.application.port.out.ChatModelPort;

/**
 * Cancels the generation that is currently running.
 */
public class CancelTurnService implements CancelTurnUseCase {

  private final ChatModelPort chatModelPort;

  /**
   * Creates the service.
   *
   * @param chatModelPort language model whose active stream is cancelled
   */
  public CancelTurnService(ChatModelPort chatModelPort) {
    this.chatModelPort = chatModelPort;
  }

  @Override
  public void execute(CancelTurnCommand command) {
    chatModelPort.cancelActive();
  }
}
