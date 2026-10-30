package dev.localllm.harness.application.fixture;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.localllm.harness.application.model.RunningModelInfo;
import dev.localllm.harness.application.port.out.RunningModelsPort;

/**
 * Fixed list of loaded models.
 */
public final class FakeRunningModelsPort implements RunningModelsPort {

  /** {@link FakeModelCatalogPort#QWEN} loaded fully into GPU memory. */
  public static final RunningModelInfo QWEN = new RunningModelInfo(
      "qwen3.5:27b", 19_744_124_896L, 19_744_124_896L, 32768,
      Instant.parse("2026-10-29T16:32:51Z"));

  private final List<RunningModelInfo> models;
  private @Nullable RuntimeException failure;

  /**
   * Creates the port with the given loaded models.
   *
   * @param models loaded models
   */
  public FakeRunningModelsPort(RunningModelInfo... models) {
    this.models = List.of(models);
  }

  /**
   * Makes every following call fail, as when the server is down.
   *
   * @param error failure to throw
   * @return this port
   */
  public FakeRunningModelsPort failWith(RuntimeException error) {
    this.failure = error;
    return this;
  }

  @Override
  public List<RunningModelInfo> listRunning() {
    if (failure != null) {
      throw failure;
    }
    return models;
  }
}
