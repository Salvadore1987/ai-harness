package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.SessionSummaryResult;
import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.SessionId;

/**
 * Mutable state of one CLI run: current session, provider, model and display flags.
 */
public final class ReplSession {

  private final Path workingDirectory;
  private final boolean verbose;
  private @Nullable SessionId sessionId;
  private @Nullable String title;
  private LlmProvider provider = LlmProvider.OLLAMA;
  private String model;
  private boolean thinkingEnabled;
  private boolean thinkingSupported = true;
  private boolean toolsSupported = true;
  private ReplMode mode = ReplMode.CHAT;

  /**
   * Creates the state for a CLI run.
   *
   * @param model initial model tag
   * @param thinkingEnabled whether thinking is requested initially
   * @param verbose whether turn statistics are printed
   * @param workingDirectory directory the harness works in
   */
  public ReplSession(
      String model, boolean thinkingEnabled, boolean verbose, Path workingDirectory) {
    this.model = model;
    this.thinkingEnabled = thinkingEnabled;
    this.verbose = verbose;
    this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
  }

  /**
   * Current session, {@code null} until the first session is started.
   *
   * @return session identifier
   */
  public @Nullable SessionId getSessionId() {
    return sessionId;
  }

  /**
   * Title of a resumed session, {@code null} for a new one.
   *
   * @return session title
   */
  public @Nullable String getTitle() {
    return title;
  }

  /**
   * Switches to a stored session.
   *
   * @param resumed the session to continue
   */
  public void resume(SessionSummaryResult resumed) {
    this.sessionId = resumed.sessionId();
    this.title = resumed.title();
  }

  /**
   * Switches to a new, empty session.
   *
   * @param newSessionId identifier of the new session
   */
  public void startNew(SessionId newSessionId) {
    this.sessionId = newSessionId;
    this.title = null;
  }

  /**
   * Provider of the current session.
   *
   * @return provider
   */
  public LlmProvider getProvider() {
    return provider;
  }

  /**
   * Current model tag.
   *
   * @return model tag
   */
  public String getModel() {
    return model;
  }

  /**
   * Whether thinking is requested for the next turns.
   *
   * @return thinking flag
   */
  public boolean isThinkingEnabled() {
    return thinkingEnabled;
  }

  /**
   * Changes the thinking flag.
   *
   * @param thinkingEnabled new thinking flag
   */
  public void setThinkingEnabled(boolean thinkingEnabled) {
    this.thinkingEnabled = thinkingEnabled;
  }

  /**
   * Whether the current model supports thinking.
   *
   * @return capability flag
   */
  public boolean isThinkingSupported() {
    return thinkingSupported;
  }

  /**
   * Whether the current model can call tools.
   *
   * @return capability flag
   */
  public boolean isToolsSupported() {
    return toolsSupported;
  }

  /**
   * How user messages are handled.
   *
   * @return current mode
   */
  public ReplMode getMode() {
    return mode;
  }

  /**
   * Changes how user messages are handled.
   *
   * @param mode new mode
   */
  public void setMode(ReplMode mode) {
    this.mode = mode;
  }

  /**
   * Whether turn statistics are printed.
   *
   * @return verbose flag
   */
  public boolean isVerbose() {
    return verbose;
  }

  /**
   * Directory the harness works in.
   *
   * @return working directory
   */
  public Path getWorkingDirectory() {
    return workingDirectory;
  }

  /**
   * Selects a model by name before it is validated, for example the model of a resumed session.
   *
   * @param modelName model tag
   */
  public void applyModelName(String modelName) {
    this.model = modelName;
  }

  /**
   * Switches to a validated model; thinking is turned off if the model cannot think.
   *
   * @param selected the validated model
   * @return {@code true} if thinking had to be turned off
   */
  public boolean applyModel(ModelSummaryResult selected) {
    this.model = selected.name();
    this.thinkingSupported = selected.thinkingSupported();
    this.toolsSupported = selected.toolsSupported();
    boolean disabled = thinkingEnabled && !thinkingSupported;
    if (disabled) {
      thinkingEnabled = false;
    }
    return disabled;
  }
}
