package dev.localllm.harness.application.fixture;

import java.util.ArrayList;
import java.util.List;

import dev.localllm.harness.application.dto.result.CompactionResult;
import dev.localllm.harness.application.dto.result.TurnResult;
import dev.localllm.harness.application.port.out.TurnOutputPort;

/**
 * Records everything presented to the user.
 */
public final class RecordingTurnOutputPort implements TurnOutputPort {

  private final List<String> thinking = new ArrayList<>();
  private final List<String> content = new ArrayList<>();
  private final List<TurnResult> results = new ArrayList<>();
  private final List<CompactionResult> compactions = new ArrayList<>();

  @Override
  public void onThinking(String text) {
    thinking.add(text);
  }

  @Override
  public void onContent(String text) {
    content.add(text);
  }

  @Override
  public void onComplete(TurnResult result) {
    results.add(result);
  }

  @Override
  public void onCompaction(CompactionResult result) {
    compactions.add(result);
  }

  /**
   * Automatic compactions presented so far.
   *
   * @return compaction results
   */
  public List<CompactionResult> compactions() {
    return List.copyOf(compactions);
  }

  /**
   * Reasoning fragments presented so far.
   *
   * @return thinking fragments
   */
  public List<String> thinking() {
    return List.copyOf(thinking);
  }

  /**
   * Answer fragments presented so far.
   *
   * @return content fragments
   */
  public List<String> content() {
    return List.copyOf(content);
  }

  /**
   * Finished turns presented so far.
   *
   * @return turn results
   */
  public List<TurnResult> results() {
    return List.copyOf(results);
  }
}
