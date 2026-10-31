package dev.localllm.harness.adapter.ollama.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import dev.localllm.harness.adapter.ollama.dto.OllamaRunningModels;
import dev.localllm.harness.application.model.RunningModelInfo;

/**
 * Converts an entry of {@code /api/ps} into the core loaded model description.
 */
@Mapper
public interface RunningModelOllamaMapper {

  /**
   * Converts a loaded model; sizes the server did not report become zero.
   *
   * @param model entry of {@code /api/ps} with a name
   * @return loaded model description
   */
  @Mapping(target = "sizeBytes", source = "size", defaultValue = "0L")
  @Mapping(target = "vramBytes", source = "sizeVram", defaultValue = "0L")
  RunningModelInfo toRunningModelInfo(OllamaRunningModels.RunningModel model);

  /**
   * Drops the server's time zone: only the moment matters.
   *
   * @param time timestamp with the server's offset
   * @return the same moment, or {@code null}
   */
  default @Nullable Instant toInstant(@Nullable OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }
}
