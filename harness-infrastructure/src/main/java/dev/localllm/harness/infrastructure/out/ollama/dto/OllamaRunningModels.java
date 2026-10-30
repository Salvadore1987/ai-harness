package dev.localllm.harness.infrastructure.out.ollama.dto;

import java.time.OffsetDateTime;
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
   * @param size memory the loaded model takes, in bytes
   * @param sizeVram part of {@code size} held in GPU memory
   * @param contextLength context window the model was loaded with
   * @param expiresAt when Ollama unloads the idle model
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RunningModel(
      @Nullable String name,
      @Nullable Long size,
      @JsonProperty("size_vram") @Nullable Long sizeVram,
      @JsonProperty("context_length") @Nullable Integer contextLength,
      @JsonProperty("expires_at") @Nullable OffsetDateTime expiresAt) {
  }
}
