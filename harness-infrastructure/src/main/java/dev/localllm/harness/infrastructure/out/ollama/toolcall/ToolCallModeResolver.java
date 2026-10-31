package dev.localllm.harness.infrastructure.out.ollama.toolcall;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.localllm.harness.infrastructure.out.ollama.config.OllamaProperties;
import dev.localllm.harness.infrastructure.out.ollama.config.OllamaProperties.ToolCallModeType;

/**
 * Selects the tool call mode of a model by the longest matching name prefix in
 * {@code harness.tool-call-modes}; models no prefix matches use
 * {@code harness.default-tool-call-mode}. Prefixes are compared ignoring case.
 */
@Component
public class ToolCallModeResolver {

  private final Map<String, ToolCallModeType> modes;
  private final ToolCallModeType defaultMode;
  private final NativeToolCallMode nativeMode;
  private final TextJsonToolCallMode textJsonMode;

  /**
   * Creates the resolver.
   *
   * @param properties Ollama settings with the modes by prefix
   * @param nativeMode native tool calling
   * @param textJsonMode tool calls written as text
   */
  public ToolCallModeResolver(OllamaProperties properties, NativeToolCallMode nativeMode,
      TextJsonToolCallMode textJsonMode) {
    this.modes = properties.toolCallModes();
    this.defaultMode = properties.defaultToolCallMode();
    this.nativeMode = nativeMode;
    this.textJsonMode = textJsonMode;
  }

  /**
   * Mode type configured for a model.
   *
   * @param model model tag, for example {@code codellama:7b}
   * @return configured mode type
   */
  public ToolCallModeType type(String model) {
    String name = model.toLowerCase(Locale.ROOT);
    ToolCallModeType type = defaultMode;
    int matched = -1;
    for (Map.Entry<String, ToolCallModeType> entry : modes.entrySet()) {
      String prefix = entry.getKey().toLowerCase(Locale.ROOT);
      if (name.startsWith(prefix) && prefix.length() > matched) {
        type = entry.getValue();
        matched = prefix.length();
      }
    }
    return type;
  }

  /**
   * Mode used for a model.
   *
   * @param model model tag
   * @return the mode
   */
  public ToolCallMode resolve(String model) {
    return switch (type(model)) {
      case NATIVE -> nativeMode;
      case TEXT_JSON -> textJsonMode;
    };
  }
}
