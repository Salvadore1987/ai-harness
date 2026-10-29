package dev.localllm.harness.infrastructure.in.cli.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.application.dto.command.CancelTurnCommand;
import dev.localllm.harness.application.dto.command.SendMessageCommand;
import dev.localllm.harness.application.dto.command.StartSessionCommand;
import dev.localllm.harness.application.dto.command.SwitchModelCommand;
import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.query.ListModelsCriteria;
import dev.localllm.harness.infrastructure.in.cli.ReplSession;

/**
 * Builds use case commands from REPL input and the CLI state.
 */
@Mapper
public interface ReplCliMapper {

  /**
   * Builds the command for a chat turn.
   *
   * @param session CLI state with session, model and thinking flag
   * @param text user message
   * @return send command
   */
  @Mapping(target = "sessionId", source = "session.sessionId")
  @Mapping(target = "model", source = "session.model")
  @Mapping(target = "thinkingEnabled", source = "session.thinkingEnabled")
  @Mapping(target = "text", source = "text")
  SendMessageCommand toSendMessageCommand(ReplSession session, String text);

  /**
   * Builds the command that cancels the running turn.
   *
   * @param session CLI state
   * @return cancel command
   */
  CancelTurnCommand toCancelTurnCommand(ReplSession session);

  /**
   * Builds the command that replaces the current session.
   *
   * @param session CLI state; its current session becomes the previous one
   * @return start command
   */
  @Mapping(target = "previousSessionId", source = "sessionId")
  StartSessionCommand toStartSessionCommand(ReplSession session);

  /**
   * Builds the command that switches the model.
   *
   * @param model requested model tag
   * @return switch command
   */
  @Mapping(target = "model", source = "model")
  SwitchModelCommand toSwitchModelCommand(String model);

  /**
   * Builds the command that toggles thinking for the current model.
   *
   * @param session CLI state with the current model
   * @param enabled requested thinking state
   * @return toggle command
   */
  @Mapping(target = "model", source = "session.model")
  @Mapping(target = "enabled", source = "enabled")
  ToggleThinkingCommand toToggleThinkingCommand(ReplSession session, boolean enabled);

  /**
   * Builds the model list criteria.
   *
   * @param namePrefix name prefix, empty for all models
   * @return list criteria
   */
  @Mapping(target = "namePrefix", source = "namePrefix")
  ListModelsCriteria toListModelsCriteria(String namePrefix);
}
