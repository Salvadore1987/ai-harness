package dev.localllm.harness.infrastructure.in.cli.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;
import dev.localllm.harness.application.dto.command.CompactSessionCommand;
import dev.localllm.harness.application.dto.command.PurgeSessionsCommand;
import dev.localllm.harness.application.dto.command.ResumeSessionCommand;
import dev.localllm.harness.application.dto.command.RunAgentTurnCommand;
import dev.localllm.harness.application.dto.command.SaveAnswerCommand;
import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.command.SwitchModelCommand;
import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.query.ContextUsageCriteria;
import dev.localllm.harness.application.dto.query.ListModelsCriteria;
import dev.localllm.harness.application.dto.query.ListRunningModelsCriteria;
import dev.localllm.harness.application.dto.query.ListSessionsCriteria;
import dev.localllm.harness.application.dto.query.ListToolsCriteria;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.infrastructure.in.cli.ReplSession;

/**
 * Builds use case commands and queries from REPL input and the CLI state.
 */
@Mapper
public interface ReplCliMapper {

  /**
   * Builds the command for a chat turn.
   *
   * @param session CLI state with session, directory, model and thinking flag
   * @param text user message, with file mentions already expanded
   * @return send command
   */
  @Mapping(target = "sessionId", source = "session.sessionId")
  @Mapping(target = "workingDirectory", source = "session.workingDirectory")
  @Mapping(target = "provider", source = "session.provider")
  @Mapping(target = "model", source = "session.model")
  @Mapping(target = "thinkingEnabled", source = "session.thinkingEnabled")
  @Mapping(target = "text", source = "text")
  SendMessageCommand toSendMessageCommand(ReplSession session, String text);

  /**
   * Builds the command for an agent turn.
   *
   * @param session CLI state with session, directory, model and thinking flag
   * @param text user message, with file mentions already expanded
   * @return agent command
   */
  @Mapping(target = "sessionId", source = "session.sessionId")
  @Mapping(target = "workingDirectory", source = "session.workingDirectory")
  @Mapping(target = "provider", source = "session.provider")
  @Mapping(target = "model", source = "session.model")
  @Mapping(target = "thinkingEnabled", source = "session.thinkingEnabled")
  @Mapping(target = "text", source = "text")
  RunAgentTurnCommand toRunAgentTurnCommand(ReplSession session, String text);

  /**
   * Builds the command that cancels the running turn.
   *
   * @param session CLI state
   * @return cancel command
   */
  CancelTurnCommand toCancelTurnCommand(ReplSession session);

  /**
   * Builds the command that starts a new session.
   *
   * @param session CLI state
   * @return start command
   */
  StartSessionCommand toStartSessionCommand(ReplSession session);

  /**
   * Builds the command that continues a stored session.
   *
   * @param session CLI state with the working directory
   * @param reference session identifier or prefix; empty for the latest session
   * @return resume command
   */
  @Mapping(target = "workingDirectory", source = "session.workingDirectory")
  @Mapping(target = "reference", source = "reference")
  ResumeSessionCommand toResumeSessionCommand(ReplSession session, String reference);

  /**
   * Builds the command that compacts the current session.
   *
   * @param session CLI state with session, provider and model
   * @return compact command
   */
  CompactSessionCommand toCompactSessionCommand(ReplSession session);

  /**
   * Builds the command that saves the latest answer; no turn is running, so none is skipped.
   *
   * @param session CLI state with session and working directory
   * @param path target file, empty for a generated name
   * @return save command
   */
  @Mapping(target = "sessionId", source = "session.sessionId")
  @Mapping(target = "workingDirectory", source = "session.workingDirectory")
  @Mapping(target = "path", source = "path")
  @Mapping(target = "skipCurrentTurn", constant = "false")
  SaveAnswerCommand toSaveAnswerCommand(ReplSession session, String path);

  /**
   * Builds the command that switches the model within the provider of the session.
   *
   * @param session CLI state with the current provider
   * @param model requested model tag
   * @return switch command
   */
  @Mapping(target = "provider", source = "session.provider")
  @Mapping(target = "model", source = "model")
  SwitchModelCommand toSwitchModelCommand(ReplSession session, String model);

  /**
   * Builds the command that toggles thinking for the current model.
   *
   * @param session CLI state with the current provider and model
   * @param enabled requested thinking state
   * @return toggle command
   */
  @Mapping(target = "provider", source = "session.provider")
  @Mapping(target = "model", source = "session.model")
  @Mapping(target = "enabled", source = "enabled")
  ToggleThinkingCommand toToggleThinkingCommand(ReplSession session, boolean enabled);

  /**
   * Builds the model list criteria.
   *
   * @param provider provider whose models are listed
   * @param namePrefix name prefix, empty for all models
   * @return list criteria
   */
  @Mapping(target = "provider", source = "provider")
  @Mapping(target = "namePrefix", source = "namePrefix")
  ListModelsCriteria toListModelsCriteria(LlmProvider provider, String namePrefix);

  /**
   * Builds the loaded model list criteria.
   *
   * @param namePrefix name prefix, empty for all loaded models
   * @return list criteria
   */
  @Mapping(target = "namePrefix", source = "namePrefix")
  ListRunningModelsCriteria toListRunningModelsCriteria(String namePrefix);

  /**
   * Builds the session list criteria for the working directory.
   *
   * @param session CLI state with the working directory
   * @return list criteria
   */
  ListSessionsCriteria toListSessionsCriteria(ReplSession session);

  /**
   * Builds the command that deletes the stored sessions of the working directory.
   *
   * @param session CLI state with the working directory and the session to keep
   * @return purge command
   */
  @Mapping(target = "currentSessionId", source = "sessionId")
  PurgeSessionsCommand toPurgeSessionsCommand(ReplSession session);

  /**
   * Builds the context usage criteria for the current session and model.
   *
   * @param session CLI state
   * @return usage criteria
   */
  ContextUsageCriteria toContextUsageCriteria(ReplSession session);

  /**
   * Builds the tool list criteria.
   *
   * @param namePrefix name prefix, empty for all tools
   * @return list criteria
   */
  @Mapping(target = "namePrefix", source = "namePrefix")
  ListToolsCriteria toListToolsCriteria(String namePrefix);
}
