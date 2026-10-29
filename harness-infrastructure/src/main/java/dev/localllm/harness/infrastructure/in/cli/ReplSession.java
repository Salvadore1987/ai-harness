package dev.localllm.harness.infrastructure.in.cli;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.model.SessionId;

/**
 * Mutable state of one CLI run: current session, model and display flags.
 */
public final class ReplSession {

  private final Path workingDirectory;
  private final boolean verbose;
  private @Nullable SessionId sessionId;
  private String model;
  private boolean thinkingEnabled;
  private boolean thinkingSupported = true;

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
    this.workingDirectory = workingDirectory;
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
   * Replaces the current session.
   *
   * @param sessionId new session identifier
   */
  public void setSessionId(SessionId sessionId) {
    this.sessionId = sessionId;
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
   * Switches to a validated model; thinking is turned off if the model cannot think.
   *
   * @param selected the validated model
   * @return {@code true} if thinking had to be turned off
   */
  public boolean applyModel(ModelSummaryResult selected) {
    this.model = selected.name();
    this.thinkingSupported = selected.thinkingSupported();
    boolean disabled = thinkingEnabled && !thinkingSupported;
    if (disabled) {
      thinkingEnabled = false;
    }
    return disabled;
  }
}
