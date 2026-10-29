package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;
import java.util.List;

import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.FakeModelCatalogPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.fixture.RecordingTurnOutputPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.service.CancelTurnService;
import dev.localllm.harness.application.service.ListModelsQueryService;
import dev.localllm.harness.application.service.SendMessageService;
import dev.localllm.harness.application.service.StartSessionService;
import dev.localllm.harness.application.service.SwitchModelService;
import dev.localllm.harness.application.service.ToggleThinkingService;
import dev.localllm.harness.infrastructure.TestTerminals;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.in.cli.handlers.CliErrorReporter;
import dev.localllm.harness.infrastructure.in.cli.handlers.FallbackErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.handlers.LlmErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.handlers.ModelErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * CLI components wired to the real application services over in-memory port fakes.
 */
final class CliTestContext {

  final TestTerminals.Captured terminal = TestTerminals.interactive();
  final FakeChatModelPort chatModel = new FakeChatModelPort();
  final InMemoryConversationPort conversation = new InMemoryConversationPort();
  final RecordingTurnOutputPort output = new RecordingTurnOutputPort();
  final FakeModelCatalogPort catalog =
      new FakeModelCatalogPort(FakeModelCatalogPort.QWEN, FakeModelCatalogPort.CODELLAMA);
  final ReplCliMapper mapper = Mappers.getMapper(ReplCliMapper.class);
  final CliErrorReporter reporter = new CliErrorReporter(
      List.of(new LlmErrorHandler(), new ModelErrorHandler(), new FallbackErrorHandler()),
      terminal.terminal());
  final HarnessProperties properties = new HarnessProperties(
      FakeModelCatalogPort.QWEN.name(), "http://localhost:11434", Path.of("build/harness-home"),
      new HarnessProperties.Thinking(true, HarnessProperties.ThinkingDisplay.STREAM));

  private final TurnApplicationMapper applicationMapper =
      Mappers.getMapper(TurnApplicationMapper.class);

  SlashCommandDispatcher dispatcher() {
    return new SlashCommandDispatcher(
        new StartSessionService(conversation, applicationMapper),
        new SwitchModelService(catalog, applicationMapper),
        new ToggleThinkingService(catalog, applicationMapper),
        new ListModelsQueryService(catalog, applicationMapper),
        mapper, reporter, terminal.terminal());
  }

  TurnExecutor turnExecutor() {
    return new TurnExecutor(
        new SendMessageService(conversation, chatModel, output, applicationMapper),
        new CancelTurnService(chatModel),
        mapper, reporter, terminal.terminal());
  }

  HarnessCommand command() {
    return new HarnessCommand(
        new StartSessionService(conversation, applicationMapper),
        new SwitchModelService(catalog, applicationMapper),
        mapper, turnExecutor(),
        new ReplRunner(terminal.terminal(), turnExecutor(), dispatcher(), properties),
        reporter, properties);
  }

  ReplSession session() {
    ReplSession session = new ReplSession(
        FakeModelCatalogPort.QWEN.name(), true, false, Path.of("."));
    session.setSessionId(SessionId.newId());
    return session;
  }
}
