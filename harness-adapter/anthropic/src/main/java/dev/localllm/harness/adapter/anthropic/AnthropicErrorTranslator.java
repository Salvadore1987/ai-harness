package dev.localllm.harness.adapter.anthropic;

import org.springframework.stereotype.Component;

import dev.localllm.harness.application.exception.HarnessException;
import dev.localllm.harness.application.exception.LlmResponseException;

/**
 * Translates failures of the Anthropic SDK into harness exceptions.
 *
 * <p>The messages of SDK exceptions carry the status and the error body of the API, never the
 * API key.
 */
@Component
public class AnthropicErrorTranslator {

  /**
   * Translates a failure of a streaming request that happened before the first chunk.
   *
   * @param error failure raised by the SDK
   * @param model model of the request
   * @return the harness exception to throw
   */
  public HarnessException translateStreamFailure(Throwable error, String model) {
    return new LlmResponseException(
        "Claude API request for model " + model + " failed: " + error.getMessage(), error);
  }
}
