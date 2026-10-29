package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ToolCallModeType;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ToolCallSpringAiMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallModeResolverTest {

  private final NativeToolCallMode nativeMode = new NativeToolCallMode(
      Mappers.getMapper(ChatMessageSpringAiMapper.class),
      Mappers.getMapper(ToolCallSpringAiMapper.class));
  private final TextJsonToolCallMode textJsonMode = new TextJsonToolCallMode(
      Mappers.getMapper(ChatMessageSpringAiMapper.class), new ToolBlockParser());

  @Test
  void resolve_ByLongestPrefixIgnoringCase_FallsBackToDefault() {
    // Arrange
    ToolCallModeResolver resolver = resolver(Map.of(
        "qwen", ToolCallModeType.TEXT_JSON,
        "qwen3.5", ToolCallModeType.NATIVE,
        "CodeLlama", ToolCallModeType.TEXT_JSON), ToolCallModeType.NATIVE);

    // Act & Assert
    assertThat(resolver.resolve("qwen3.5:27b")).isSameAs(nativeMode);
    assertThat(resolver.resolve("qwen2.5-coder:7b")).isSameAs(textJsonMode);
    assertThat(resolver.resolve("codellama:7b")).isSameAs(textJsonMode);
    assertThat(resolver.resolve("llama3.1:8b")).isSameAs(nativeMode);
  }

  @Test
  void type_WithoutPrefixes_UsesDefault() {
    // Arrange
    ToolCallModeResolver resolver = resolver(Map.of(), ToolCallModeType.TEXT_JSON);

    // Act & Assert
    assertThat(resolver.type("qwen3.5:27b")).isEqualTo(ToolCallModeType.TEXT_JSON);
  }

  private ToolCallModeResolver resolver(Map<String, ToolCallModeType> modes,
      ToolCallModeType defaultMode) {
    HarnessProperties defaults =
        TestProperties.create(Path.of("build/harness-home"), "http://localhost:11434", false);
    HarnessProperties properties = new HarnessProperties(defaults.model(),
        defaults.ollamaBaseUrl(), defaults.home(), defaults.thinking(), defaults.context(),
        defaults.agent(), defaults.tools(), modes, defaultMode);
    return new ToolCallModeResolver(properties, nativeMode, textJsonMode);
  }
}
