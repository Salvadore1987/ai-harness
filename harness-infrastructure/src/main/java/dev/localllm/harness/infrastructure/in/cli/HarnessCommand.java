package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.exception.HarnessException;
import dev.localllm.harness.application.model.TurnStatus;
import dev.localllm.harness.application.port.in.ResumeSessionUseCase;
import dev.localllm.harness.application.port.in.StartSessionUseCase;
import dev.localllm.harness.application.port.in.SwitchModelUseCase;
import dev.localllm.harness.infrastructure.config.DisplayOptions;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.in.cli.handlers.CliErrorReporter;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Entry command: {@code harness [--model M] [--cwd DIR] [--resume [ID]] [--mode chat|agent]
 * [--no-think] [--verbose] [-p PROMPT]}.
 *
 * <p>Without {@code -p} it opens the REPL; with {@code -p} it runs one turn, prints the answer
 * and exits with 0 on success or 1 on failure. The subcommands {@link ListModelsCommand} and
 * {@link RunningModelsCommand} are attached by {@link PicocliRunner}.
 */
@Component
@Command(
    name = "harness",
    mixinStandardHelpOptions = true,
    version = "harness 0.1.0",
    description = "Local AI harness for Ollama models.")
public class HarnessCommand implements Callable<Integer> {

  private static final int SUCCESS = 0;
  private static final int FAILURE = 1;

  @Option(names = "--model", description = "Model tag, defaults to harness.model.")
  private @Nullable String model;

  @Option(names = "--cwd", description = "Working directory, defaults to the current one.")
  private @Nullable Path workingDirectory;

  @Option(names = "--resume", arity = "0..1", fallbackValue = "", paramLabel = "ID",
      description = "Continue a stored session: the latest of the directory, or the given id.")
  private @Nullable String resume;

  @Option(names = "--no-think", description = "Start with the thinking mode turned off.")
  private boolean noThink;

  @Option(names = "--mode", paramLabel = "MODE", converter = ReplModeConverter.class,
      description = "chat (default) or agent: the model may read files with tools.")
  private ReplMode mode = ReplMode.CHAT;

  @Option(names = "--verbose",
      description = "Print token statistics and the full output of tool calls.")
  private boolean verbose;

  @Option(names = {"-p", "--print"}, description = "Run one prompt, print the answer, exit.")
  private @Nullable String prompt;

  private final StartSessionUseCase startSessionUseCase;
  private final ResumeSessionUseCase resumeSessionUseCase;
  private final SwitchModelUseCase switchModelUseCase;
  private final ReplCliMapper mapper;
  private final TurnExecutor turnExecutor;
  private final ReplRunner replRunner;
  private final CliErrorReporter errorReporter;
  private final HarnessProperties properties;
  private final DisplayOptions displayOptions;

  /**
   * Creates the command.
   *
   * @param startSessionUseCase starts the session of this run
   * @param resumeSessionUseCase finds the session for {@code --resume}
   * @param switchModelUseCase validates the startup model
   * @param mapper builds commands from the CLI state
   * @param turnExecutor runs the single turn of {@code -p}
   * @param replRunner interactive loop
   * @param errorReporter prints startup failures
   * @param properties harness defaults
   * @param displayOptions display flags shared with the terminal presentation
   */
  public HarnessCommand(
      StartSessionUseCase startSessionUseCase,
      ResumeSessionUseCase resumeSessionUseCase,
      SwitchModelUseCase switchModelUseCase,
      ReplCliMapper mapper,
      TurnExecutor turnExecutor,
      ReplRunner replRunner,
      CliErrorReporter errorReporter,
      HarnessProperties properties,
      DisplayOptions displayOptions) {
    this.startSessionUseCase = startSessionUseCase;
    this.resumeSessionUseCase = resumeSessionUseCase;
    this.switchModelUseCase = switchModelUseCase;
    this.mapper = mapper;
    this.turnExecutor = turnExecutor;
    this.replRunner = replRunner;
    this.errorReporter = errorReporter;
    this.properties = properties;
    this.displayOptions = displayOptions;
  }

  @Override
  public Integer call() {
    ReplSession session = new ReplSession(
        model == null ? properties.model() : model,
        properties.thinking().enabled() && !noThink,
        verbose,
        workingDirectory == null ? Path.of("") : workingDirectory);
    session.setMode(mode);
    displayOptions.setVerbose(verbose);
    if (!openSession(session)) {
      return FAILURE;
    }
    boolean modelReady = prepareModel(session);

    String singlePrompt = prompt;
    if (singlePrompt != null) {
      if (!modelReady) {
        return FAILURE;
      }
      Optional<TurnResult> result = turnExecutor.execute(session, singlePrompt);
      return result.filter(turn -> turn.status() == TurnStatus.COMPLETED).isPresent()
          ? SUCCESS : FAILURE;
    }
    replRunner.run(session);
    return SUCCESS;
  }

  private boolean openSession(ReplSession session) {
    String reference = resume;
    if (reference == null) {
      session.startNew(
          startSessionUseCase.execute(mapper.toStartSessionCommand(session)).sessionId());
      return true;
    }
    try {
      SessionSummaryResult resumed =
          resumeSessionUseCase.execute(mapper.toResumeSessionCommand(session, reference));
      session.resume(resumed);
      if (model == null) {
        session.applyModelName(resumed.model());
      }
      return true;
    } catch (HarnessException e) {
      errorReporter.report(e);
      return false;
    }
  }

  private boolean prepareModel(ReplSession session) {
    try {
      ModelSummaryResult selected =
          switchModelUseCase.execute(mapper.toSwitchModelCommand(session, session.getModel()));
      session.applyModel(selected);
      return true;
    } catch (HarnessException e) {
      errorReporter.report(e);
      return false;
    }
  }
}
