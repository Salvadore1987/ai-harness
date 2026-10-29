package dev.localllm.harness.infrastructure.config;

import org.springframework.stereotype.Component;

/**
 * Display flags chosen on the command line, shared by the CLI and the terminal presentation.
 */
@Component
public class DisplayOptions {

  private volatile boolean verbose;

  /**
   * Whether tool output and statistics are printed in full.
   *
   * @return verbose flag
   */
  public boolean isVerbose() {
    return verbose;
  }

  /**
   * Changes the verbose flag.
   *
   * @param verbose new flag
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }
}
