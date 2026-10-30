package dev.localllm.harness.application.mapper;

import java.nio.file.Path;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.ContextUsageResult;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.RunningModelResult;
import dev.localllm.harness.application.dto.result.SavedAnswerResult;
import dev.localllm.harness.application.dto.result.SessionResult;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;
import dev.localllm.harness.application.dto.result.ToolSummaryResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.model.ChatCompletion;
import dev.localllm.harness.application.model.CompactionOutcome;
import dev.localllm.harness.application.model.ContextUsage;
import dev.localllm.harness.application.model.ModelCapability;
import dev.localllm.harness.application.model.ModelInfo;
import dev.localllm.harness.application.model.RunningModelInfo;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.model.SessionInfo;
import dev.localllm.harness.application.model.ToolSpec;

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
   * Converts a loaded model into its result.
   *
   * @param model loaded model
   * @return loaded model result
   */
  RunningModelResult toRunningModelResult(RunningModelInfo model);

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

  /**
   * Converts stored session metadata into its summary.
   *
   * @param session session metadata
   * @return session summary
   */
  SessionSummaryResult toSessionSummary(SessionInfo session);

  /**
   * Converts a compaction outcome into its result.
   *
   * @param outcome compaction outcome
   * @return compaction result
   */
  CompactionResult toCompactionResult(CompactionOutcome outcome);

  /**
   * Converts a context measurement into its result.
   *
   * @param usage measured usage
   * @return usage result
   */
  ContextUsageResult toContextUsageResult(ContextUsage usage);

  /**
   * Converts a tool description into its summary.
   *
   * @param tool tool description
   * @return tool summary
   */
  ToolSummaryResult toToolSummary(ToolSpec tool);

  /**
   * Describes a written answer file.
   *
   * @param file absolute path of the file
   * @param characters length of the saved text
   * @return saved answer result
   */
  @Mapping(target = "file", source = "file")
  @Mapping(target = "characters", source = "characters")
  SavedAnswerResult toSavedAnswerResult(Path file, int characters);
}
