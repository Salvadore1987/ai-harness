package dev.localllm.harness.application.exception;

/**
 * The model server cannot be reached.
 */
public class LlmUnavailableException extends HarnessException {

  private static final long serialVersionUID = 1L;

  /** Error code of this failure. */
  public static final String CODE = "500001";

  private final String baseUrl;

  /**
   * Creates the exception.
   *
   * @param baseUrl address of the model server that did not respond
   * @param cause underlying connection failure
   */
  public LlmUnavailableException(String baseUrl, Throwable cause) {
    super(CODE, "Ollama is not reachable at " + baseUrl, cause);
    this.baseUrl = baseUrl;
  }

  /**
   * Address of the model server that did not respond.
   *
   * @return base URL of the model server
   */
  public String getBaseUrl() {
    return baseUrl;
  }
}
