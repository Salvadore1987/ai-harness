package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.fixture.FakeChatModelPort;
import dev.localllm.harness.application.fixture.FakeModelCatalogPort;
import dev.localllm.harness.application.fixture.FakeSessionCatalogPort;
import dev.localllm.harness.application.fixture.FixedContextBudgetPort;
import dev.localllm.harness.application.fixture.InMemoryConversationPort;
import dev.localllm.harness.application.fixture.RecordingTurnOutputPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ContextPolicy;
import dev.localllm.harness.application.model.SessionId;
import dev.localllm.harness.application.service.CancelTurnService;
import dev.localllm.harness.application.service.CompactSessionService;
import dev.localllm.harness.application.service.ContextMeter;
import dev.localllm.harness.application.service.ConversationCompactor;
import dev.localllm.harness.application.service.GetContextUsageQueryService;
import dev.localllm.harness.application.service.ListModelsQueryService;
import dev.localllm.harness.application.service.ListSessionsQueryService;
import dev.localllm.harness.application.service.ResumeSessionService;
import dev.localllm.harness.application.service.SendMessageService;
import dev.localllm.harness.application.service.StartSessionService;
import dev.localllm.harness.application.service.SwitchModelService;
import dev.localllm.harness.application.service.ToggleThinkingService;
import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.TestTerminals;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.in.cli.handlers.CliErrorReporter;
import dev.localllm.harness.infrastructure.in.cli.handlers.FallbackErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.handlers.LlmErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.handlers.ModelErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.handlers.SessionErrorHandler;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * CLI components wired to the real application services over in-memory port fakes.
 */
final class CliTestContext {

  static final Path CWD = Path.of("/work/project");
  static final ContextPolicy POLICY = new ContextPolicy(0.8, 2, 4.0);

  final TestTerminals.Captured terminal = TestTerminals.interactive();
  final FakeChatModelPort chatModel = new FakeChatModelPort();
  final InMemoryConversationPort conversation = new InMemoryConversationPort();
  final FakeSessionCatalogPort catalog = new FakeSessionCatalogPort();
  final FixedContextBudgetPort budget = new FixedContextBudgetPort(65536);
  final RecordingTurnOutputPort output = new RecordingTurnOutputPort();
  final FakeModelCatalogPort models =
      new FakeModelCatalogPort(FakeModelCatalogPort.QWEN, FakeModelCatalogPort.CODELLAMA);
  final ReplCliMapper mapper = Mappers.getMapper(ReplCliMapper.class);
  final CliErrorReporter reporter = new CliErrorReporter(
      List.of(new LlmErrorHandler(), new ModelErrorHandler(), new SessionErrorHandler(),
          new FallbackErrorHandler()),
      terminal.terminal());
  final HarnessProperties properties =
      TestProperties.create(Path.of("build/harness-home"), "http://localhost:11434", false);

  private final TurnApplicationMapper applicationMapper =
      Mappers.getMapper(TurnApplicationMapper.class);
  private final ContextMeter meter = new ContextMeter(budget, POLICY);
  private final ConversationCompactor compactor =
      new ConversationCompactor(conversation, chatModel, meter, POLICY);

  SlashCommandDispatcher dispatcher() {
    return new SlashCommandDispatcher(sessionCommands(), modelCommands(), reporter,
        terminal.terminal());
  }

  SessionSlashCommands sessionCommands() {
    return new SessionSlashCommands(
        new StartSessionService(applicationMapper),
        new ResumeSessionService(catalog, applicationMapper),
        new ListSessionsQueryService(catalog, applicationMapper),
        new CompactSessionService(compactor, applicationMapper),
        new GetContextUsageQueryService(conversation, meter, applicationMapper),
        mapper, terminal.terminal());
  }

  ModelSlashCommands modelCommands() {
    return new ModelSlashCommands(
        new SwitchModelService(models, applicationMapper),
        new ToggleThinkingService(models, applicationMapper),
        new ListModelsQueryService(models, applicationMapper),
        mapper, terminal.terminal());
  }

  TurnExecutor turnExecutor() {
    return new TurnExecutor(
        new SendMessageService(conversation, catalog, chatModel, output, meter, compactor,
            applicationMapper, Clock.systemUTC()),
        new CancelTurnService(chatModel),
        mapper, new FileMentionExpander(), reporter, terminal.terminal());
  }

  HarnessCommand command() {
    return new HarnessCommand(
        new StartSessionService(applicationMapper),
        new ResumeSessionService(catalog, applicationMapper),
        new SwitchModelService(models, applicationMapper),
        mapper, turnExecutor(),
        new ReplRunner(terminal.terminal(), turnExecutor(), dispatcher(), properties),
        reporter, properties);
  }

  ReplSession session() {
    ReplSession session = new ReplSession(FakeModelCatalogPort.QWEN.name(), true, false, CWD);
    session.startNew(SessionId.newId());
    return session;
  }
}
