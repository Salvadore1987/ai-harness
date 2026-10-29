package dev.localllm.harness.infrastructure.in.cli;

/**
 * What the REPL does after a slash command.
 */
public enum SlashOutcome {
  /** Keep reading input. */
  CONTINUE,
  /** Leave the REPL. */
  EXIT
}
