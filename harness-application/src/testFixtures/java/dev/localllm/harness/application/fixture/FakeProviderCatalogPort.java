package dev.localllm.harness.application.fixture;

import java.util.List;

import dev.localllm.harness.application.model.LlmProvider;
import dev.localllm.harness.application.model.ProviderInfo;
import dev.localllm.harness.application.port.out.ProviderCatalogPort;

/**
 * Fixed list of providers.
 */
public final class FakeProviderCatalogPort implements ProviderCatalogPort {

  /** Ollama, available, starting with {@code qwen3.5:27b}. */
  public static final ProviderInfo OLLAMA =
      new ProviderInfo(LlmProvider.OLLAMA, true, "qwen3.5:27b");

  /** Anthropic, available, starting with {@code claude-opus-5-5}. */
  public static final ProviderInfo ANTHROPIC =
      new ProviderInfo(LlmProvider.ANTHROPIC, true, "claude-opus-5-5");

  /** Anthropic without an API key. */
  public static final ProviderInfo ANTHROPIC_UNAVAILABLE =
      new ProviderInfo(LlmProvider.ANTHROPIC, false, "claude-opus-5-5");

  private final List<ProviderInfo> providers;

  /**
   * Creates a catalog with the given providers.
   *
   * @param providers providers in any order
   */
  public FakeProviderCatalogPort(ProviderInfo... providers) {
    this.providers = List.of(providers);
  }

  @Override
  public List<ProviderInfo> listProviders() {
    return providers;
  }
}
