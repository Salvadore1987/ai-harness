package dev.localllm.harness.infrastructure.out.tools.mapper;

import java.nio.file.Path;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.application.dto.command.SaveAnswerCommand;
import dev.localllm.harness.application.model.SessionId;

/**
 * Builds use case commands from the arguments of the answer tools.
 */
@Mapper
public interface AnswerToolMapper {

  /**
   * Builds the command of {@code save_answer}; the turn that asks to save is skipped.
   *
   * @param sessionId session of the call
   * @param workingDirectory working directory of the call
   * @param path file path given by the model, empty for a generated name
   * @return save command
   */
  @Mapping(target = "sessionId", source = "sessionId")
  @Mapping(target = "workingDirectory", source = "workingDirectory")
  @Mapping(target = "path", source = "path")
  @Mapping(target = "skipCurrentTurn", constant = "true")
  SaveAnswerCommand toSaveAnswerCommand(SessionId sessionId, Path workingDirectory, String path);
}
