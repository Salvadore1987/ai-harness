package dev.localllm.harness.infrastructure.out.ollama;

import java.nio.file.Path;

import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatResponseSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ModelInfoOllamaMapper;

/**
 * Builds the Ollama adapters against a given base URL the same way the auto-configuration does.
 */
final class OllamaTestSupport {

  static final String MODEL = "qwen3.5:27b";
  static final int NUM_CTX = 65536;
  static final String KEEP_ALIVE = "30m";

  private OllamaTestSupport() {
  }

  static OllamaChatAdapter chatAdapter(String baseUrl) {
    OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
    OllamaChatModel chatModel = OllamaChatModel.builder()
        .ollamaApi(api)
        .options(OllamaChatOptions.builder()
            .model(MODEL)
            .numCtx(NUM_CTX)
            .keepAlive(KEEP_ALIVE)
            .build())
        .build();
    return new OllamaChatAdapter(
        ChatClient.builder(chatModel),
        Mappers.getMapper(ChatMessageSpringAiMapper.class),
        Mappers.getMapper(ChatResponseSpringAiMapper.class),
        translator(baseUrl));
  }

  static OllamaModelCatalogAdapter catalogAdapter(String baseUrl) {
    return new OllamaModelCatalogAdapter(
        OllamaApi.builder().baseUrl(baseUrl).build(),
        Mappers.getMapper(ModelInfoOllamaMapper.class),
        translator(baseUrl));
  }

  static OllamaErrorTranslator translator(String baseUrl) {
    return new OllamaErrorTranslator(new HarnessProperties(
        MODEL, baseUrl, Path.of("build/harness-home"),
        new HarnessProperties.Thinking(true, HarnessProperties.ThinkingDisplay.STREAM)));
  }
}
