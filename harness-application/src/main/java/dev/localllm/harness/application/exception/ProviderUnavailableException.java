package dev.localllm.harness.application.exception;

import dev.localllm.harness.application.model.LlmProvider;

/**
 * The requested LLM provider cannot be used in this run, for example because its API key is not
 * set.
 */
public class ProviderUnavailableException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "500003";

  private final LlmProvider provider;

  /**
   * Creates the exception.
   *
   * @param provider the unavailable provider
   */
  public ProviderUnavailableException(LlmProvider provider) {
    super(CODE, "Provider " + provider + " is not available");
    this.provider = provider;
  }

  /**
   * The unavailable provider.
   *
   * @return provider
   */
  public LlmProvider getProvider() {
    return provider;
  }
}
