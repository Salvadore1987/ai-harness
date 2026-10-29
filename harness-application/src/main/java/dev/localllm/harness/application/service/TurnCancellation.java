package dev.localllm.harness.application.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cancellation request shared by {@link CancelTurnService} and the agent loop, which checks it
 * between steps where no model stream is running.
 */
public class TurnCancellation {

  private final AtomicBoolean requested = new AtomicBoolean();

  /**
   * Clears a request left over from a previous turn.
   */
  public void reset() {
    requested.set(false);
  }

  /**
   * Requests cancellation of the running turn.
   */
  public void request() {
    requested.set(true);
  }

  /**
   * Tells whether cancellation was requested since the last {@link #reset()}.
   *
   * @return {@code true} if the turn should stop
   */
  public boolean isRequested() {
    return requested.get();
  }
}
