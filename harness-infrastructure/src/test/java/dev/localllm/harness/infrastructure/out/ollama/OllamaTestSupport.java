package dev.localllm.harness.infrastructure.out.ollama;

import java.nio.file.Path;
import java.util.Map;

import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import dev.localllm.harness.infrastructure.TestProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties;
import dev.localllm.harness.infrastructure.config.HarnessProperties.ToolCallModeType;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ChatResponseSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ModelInfoOllamaMapper;
import dev.localllm.harness.infrastructure.out.ollama.mapper.ToolCallSpringAiMapper;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.NativeToolCallMode;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.TextJsonToolCallMode;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.ToolBlockParser;
import dev.localllm.harness.infrastructure.out.ollama.toolcall.ToolCallModeResolver;

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
    return chatAdapter(baseUrl, Map.of());
  }

  static OllamaChatAdapter chatAdapter(String baseUrl, Map<String, ToolCallModeType> modes) {
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
        Mappers.getMapper(ChatResponseSpringAiMapper.class),
        translator(baseUrl),
        toolCallModes(modes));
  }

  static OllamaModelCatalogAdapter catalogAdapter(String baseUrl) {
    return new OllamaModelCatalogAdapter(
        OllamaApi.builder().baseUrl(baseUrl).build(),
        Mappers.getMapper(ModelInfoOllamaMapper.class),
        translator(baseUrl),
        toolCallModes(Map.of("codellama", ToolCallModeType.TEXT_JSON)));
  }

  static ToolCallModeResolver toolCallModes(Map<String, ToolCallModeType> modes) {
    ChatMessageSpringAiMapper messageMapper = Mappers.getMapper(ChatMessageSpringAiMapper.class);
    HarnessProperties defaults =
        TestProperties.create(Path.of("build/harness-home"), "http://localhost:11434", false);
    HarnessProperties properties = new HarnessProperties(defaults.model(),
        defaults.ollamaBaseUrl(), defaults.home(), defaults.thinking(), defaults.context(),
        defaults.agent(), defaults.tools(), modes, ToolCallModeType.NATIVE, defaults.mcp());
    return new ToolCallModeResolver(properties,
        new NativeToolCallMode(messageMapper, Mappers.getMapper(ToolCallSpringAiMapper.class)),
        new TextJsonToolCallMode(messageMapper, new ToolBlockParser()));
  }

  static OllamaErrorTranslator translator(String baseUrl) {
    return new OllamaErrorTranslator(
        TestProperties.create(Path.of("build/harness-home"), baseUrl, false));
  }
}
