package dev.localllm.harness.infrastructure.out.ollama;

import java.net.ConnectException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

import dev.localllm.harness.application.exception.HarnessException;
import dev.localllm.harness.application.exception.LlmResponseException;
import dev.localllm.harness.application.exception.LlmUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaErrorTranslatorTest {

  private final OllamaErrorTranslator translator =
      OllamaTestSupport.translator("http://localhost:11434");

  @ParameterizedTest
  @ValueSource(strings = {
      "404 - {\"error\":\"model 'x' not found\"}",
      "HTTP 404 - {\"error\":\"model 'x' not found\"}"})
  void isNotFound_WithEitherSpringAiMessageFormat_ReturnsTrue(String message) {
    // Act
    boolean notFound = translator.isNotFound(new NonTransientAiException(message));

    // Assert
    assertThat(notFound).isTrue();
  }

  @Test
  void isNotFound_WithOtherClientError_ReturnsFalse() {
    // Act
    boolean notFound = translator.isNotFound(new NonTransientAiException("HTTP 400 - 404 - x"));

    // Assert
    assertThat(notFound).isFalse();
  }

  @Test
  void translateRestFailure_WhenConnectionRefused_ReturnsLlmUnavailable() {
    // Arrange
    ResourceAccessException error =
        new ResourceAccessException("I/O error", new ConnectException("refused"));

    // Act
    HarnessException translated = translator.translateRestFailure(error);

    // Assert
    assertThat(translated).isInstanceOf(LlmUnavailableException.class);
    assertThat(translated.getCode()).isEqualTo(LlmUnavailableException.CODE);
  }

  @Test
  void translateRestFailure_WhenServerError_ReturnsLlmResponseError() {
    // Act
    HarnessException translated =
        translator.translateRestFailure(new TransientAiException("HTTP 500 - boom"));

    // Assert
    assertThat(translated).isInstanceOf(LlmResponseException.class)
        .hasMessageContaining("HTTP 500 - boom");
  }
}
