package dev.localllm.harness.adapter.ollama.fixture;

import java.util.Map;

import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.client.RestClient;

import dev.localllm.harness.adapter.ollama.OllamaChatAdapter;
import dev.localllm.harness.adapter.ollama.OllamaContextBudgetAdapter;
import dev.localllm.harness.adapter.ollama.OllamaErrorTranslator;
import dev.localllm.harness.adapter.ollama.OllamaModelCatalogAdapter;
import dev.localllm.harness.adapter.ollama.OllamaProviderAdapters;
import dev.localllm.harness.adapter.ollama.OllamaPsClient;
import dev.localllm.harness.adapter.ollama.config.OllamaProperties.ToolCallModeType;
import dev.localllm.harness.adapter.ollama.config.OllamaProperties;
import dev.localllm.harness.adapter.ollama.mapper.ChatMessageSpringAiMapper;
import dev.localllm.harness.adapter.ollama.mapper.ChatResponseSpringAiMapper;
import dev.localllm.harness.adapter.ollama.mapper.ModelInfoOllamaMapper;
import dev.localllm.harness.adapter.ollama.mapper.ToolCallSpringAiMapper;
import dev.localllm.harness.adapter.ollama.toolcall.NativeToolCallMode;
import dev.localllm.harness.adapter.ollama.toolcall.TextJsonToolCallMode;
import dev.localllm.harness.adapter.ollama.toolcall.ToolBlockParser;
import dev.localllm.harness.adapter.ollama.toolcall.ToolCallModeResolver;

/**
 * Builds the Ollama adapters against a given base URL the same way the auto-configuration does.
 */
public final class OllamaTestSupport {

  /** Model the adapters are configured with. */
  public static final String MODEL = "qwen3.5:27b";

  /** Keep-alive the chat model is configured with. */
  public static final String KEEP_ALIVE = "30m";

  private OllamaTestSupport() {
  }

  /**
   * Chat adapter with native tool calling for every model.
   *
   * @param baseUrl Ollama address
   * @return the adapter
   */
  public static OllamaChatAdapter chatAdapter(String baseUrl) {
    return chatAdapter(baseUrl, Map.of());
  }

  /**
   * Chat adapter with the given tool call modes.
   *
   * @param baseUrl Ollama address
   * @param modes tool call mode by model name prefix
   * @return the adapter
   */
  public static OllamaChatAdapter chatAdapter(
      String baseUrl, Map<String, ToolCallModeType> modes) {
    OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
    OllamaChatModel chatModel = OllamaChatModel.builder()
        .ollamaApi(api)
        .options(OllamaChatOptions.builder()
            .model(MODEL)
            .numCtx(OllamaTestProperties.NUM_CTX)
            .keepAlive(KEEP_ALIVE)
            .build())
        .build();
    return new OllamaChatAdapter(
        ChatClient.builder(chatModel),
        Mappers.getMapper(ChatResponseSpringAiMapper.class),
        translator(baseUrl),
        toolCallModes(modes));
  }

  /**
   * Model catalog adapter with {@code codellama} in the text tool call mode.
   *
   * @param baseUrl Ollama address
   * @return the adapter
   */
  public static OllamaModelCatalogAdapter catalogAdapter(String baseUrl) {
    return new OllamaModelCatalogAdapter(
        OllamaApi.builder().baseUrl(baseUrl).build(),
        Mappers.getMapper(ModelInfoOllamaMapper.class),
        translator(baseUrl),
        toolCallModes(Map.of("codellama", ToolCallModeType.TEXT_JSON)));
  }

  /**
   * Provider adapters over the given chat adapter, as the Ollama module registers them.
   *
   * @param chatAdapter chat adapter to use
   * @param baseUrl Ollama address of the catalog and the context window
   * @return the provider adapters
   */
  public static OllamaProviderAdapters providerAdapters(
      OllamaChatAdapter chatAdapter, String baseUrl) {
    OllamaProperties properties = OllamaTestProperties.create(baseUrl);
    return new OllamaProviderAdapters(chatAdapter, catalogAdapter(baseUrl),
        new OllamaContextBudgetAdapter(new OllamaPsClient(RestClient.builder(), properties),
            properties),
        properties);
  }

  /**
   * Tool call mode resolver with native calling as the default.
   *
   * @param modes tool call mode by model name prefix
   * @return the resolver
   */
  public static ToolCallModeResolver toolCallModes(Map<String, ToolCallModeType> modes) {
    ChatMessageSpringAiMapper messageMapper = Mappers.getMapper(ChatMessageSpringAiMapper.class);
    return new ToolCallModeResolver(
        OllamaTestProperties.create("http://localhost:11434", modes, ToolCallModeType.NATIVE),
        new NativeToolCallMode(messageMapper, Mappers.getMapper(ToolCallSpringAiMapper.class)),
        new TextJsonToolCallMode(messageMapper, new ToolBlockParser()));
  }

  /**
   * Error translator reporting the given address.
   *
   * @param baseUrl Ollama address
   * @return the translator
   */
  public static OllamaErrorTranslator translator(String baseUrl) {
    return new OllamaErrorTranslator(OllamaTestProperties.create(baseUrl));
  }
}
