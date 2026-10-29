package dev.localllm.harness.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.SessionResult;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.model.SessionId;

/**
 * Converts core models into use case results.
 */
@Mapper(imports = ModelCapability.class)
public interface TurnApplicationMapper {

  /**
   * Flattens a model turn into its result.
   *
   * @param completion outcome of the turn
   * @return turn result
   */
  @Mapping(target = "promptTokens", source = "usage.promptTokens")
  @Mapping(target = "completionTokens", source = "usage.completionTokens")
  TurnResult toTurnResult(ChatCompletion completion);

  /**
   * Converts installed model metadata into its summary.
   *
   * @param model model metadata
   * @return model summary
   */
  @Mapping(
      target = "thinkingSupported",
      expression = "java(model.capabilities().contains(ModelCapability.THINKING))")
  @Mapping(
      target = "toolsSupported",
      expression = "java(model.capabilities().contains(ModelCapability.TOOLS))")
  ModelSummaryResult toModelSummary(ModelInfo model);

  /**
   * Wraps a session identifier into the session result.
   *
   * @param sessionId new session identifier
   * @return session result
   */
  @Mapping(target = "sessionId", source = "sessionId")
  SessionResult toSessionResult(SessionId sessionId);

  /**
   * Converts an accepted toggle request into its result.
   *
   * @param command validated toggle request
   * @return toggle result
   */
  ThinkingToggleResult toThinkingToggleResult(ToggleThinkingCommand command);
}
