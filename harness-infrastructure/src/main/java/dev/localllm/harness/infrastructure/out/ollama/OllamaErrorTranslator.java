package dev.localllm.harness.infrastructure.out.ollama;

import java.net.ConnectException;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import dev.localllm.harness.application.exception.HarnessException;
import dev.localllm.harness.application.exception.LlmResponseException;
import dev.localllm.harness.application.exception.LlmUnavailableException;
import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.infrastructure.out.ollama.config.OllamaProperties;

/**
 * Translates Spring AI and HTTP client failures into harness exceptions.
 *
 * <p>Streaming calls go through {@code WebClient} and fail with {@code WebClient*Exception};
 * blocking calls go through {@code RestClient} with the Spring AI error handler and fail with
 * {@code ResourceAccessException} or an {@code AiException} whose message starts with the status
 * code.
 */
@Component
public class OllamaErrorTranslator {

  /**
   * Status prefix of Spring AI error messages: {@code "404 - ..."} from the default handler,
   * {@code "HTTP 404 - ..."} from the retry auto-configuration.
   */
  private static final Pattern NOT_FOUND_MESSAGE =
      Pattern.compile("^(HTTP )?" + HttpStatus.NOT_FOUND.value() + " - ");

  private final String baseUrl;

  /**
   * Creates the translator.
   *
   * @param properties Ollama settings with the server address
   */
  public OllamaErrorTranslator(OllamaProperties properties) {
    this.baseUrl = properties.ollamaBaseUrl();
  }

  /**
   * Translates a failure of a streaming chat request.
   *
   * @param error failure raised by the stream
   * @param model model of the request
   * @return the harness exception to throw
   */
  public HarnessException translateStreamFailure(Throwable error, String model) {
    if (isConnectionFailure(error)) {
      return new LlmUnavailableException(baseUrl, error);
    }
    WebClientResponseException response = findCause(error, WebClientResponseException.class);
    if (response != null && response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
      return new ModelNotFoundException(model);
    }
    if (response != null) {
      return new LlmResponseException(
          "Ollama answered " + response.getStatusCode().value() + ": "
              + response.getResponseBodyAsString(), error);
    }
    return new LlmResponseException("Ollama stream failed: " + error.getMessage(), error);
  }

  /**
   * Translates a failure of a blocking request such as listing models.
   *
   * @param error failure raised by the request
   * @return the harness exception to throw
   */
  public HarnessException translateRestFailure(Throwable error) {
    if (isConnectionFailure(error)) {
      return new LlmUnavailableException(baseUrl, error);
    }
    return new LlmResponseException("Ollama request failed: " + error.getMessage(), error);
  }

  /**
   * Tells whether a blocking request failed because the resource does not exist.
   *
   * @param error failure raised by the request
   * @return {@code true} for an HTTP 404 answer
   */
  public boolean isNotFound(Throwable error) {
    NonTransientAiException clientError = findCause(error, NonTransientAiException.class);
    String message = clientError == null ? null : clientError.getMessage();
    return message != null && NOT_FOUND_MESSAGE.matcher(message).find();
  }

  private static boolean isConnectionFailure(Throwable error) {
    return findCause(error, ConnectException.class) != null
        || findCause(error, WebClientRequestException.class) != null
        || findCause(error, ResourceAccessException.class) != null;
  }

  private static <T extends Throwable> @Nullable T findCause(Throwable error, Class<T> type) {
    Throwable current = error;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }
}
