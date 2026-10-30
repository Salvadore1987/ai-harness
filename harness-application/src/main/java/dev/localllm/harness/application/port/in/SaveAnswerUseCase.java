package dev.localllm.harness.application.port.in;

import dev.localllm.harness.application.dto.command.SaveAnswerCommand;
import dev.localllm.harness.application.dto.result.SavedAnswerResult;

/**
 * Saves the latest answer of a session to a file.
 */
public interface SaveAnswerUseCase {

  /** Name of the agent tool that runs this use case. */
  String TOOL_NAME = "save_answer";

  /**
   * Writes the text of the latest stored answer to a file, replacing its contents.
   *
   * @param command session, target file and which turns to consider
   * @return the written file
   */
  SavedAnswerResult execute(SaveAnswerCommand command);
}
