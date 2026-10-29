package dev.localllm.harness.infrastructure.out.ollama.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Answer of {@code GET /api/ps}: the models currently loaded by Ollama.
 *
 * @param models loaded models
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaRunningModels(@Nullable List<RunningModel> models) {

  /**
   * A loaded model.
   *
   * @param name model tag
   * @param contextLength context window the model was loaded with
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RunningModel(
      @Nullable String name,
      @JsonProperty("context_length") @Nullable Integer contextLength) {
  }
}
