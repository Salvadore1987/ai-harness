package dev.localllm.harness.infrastructure.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import dev.localllm.harness.infrastructure.out.mcp.dto.McpServersFile;
import dev.localllm.harness.infrastructure.out.memory.dto.JsonlLine;
import dev.localllm.harness.infrastructure.out.memory.dto.JsonlToolCall;
import dev.localllm.harness.infrastructure.out.ollama.dto.OllamaRunningModels;
import dev.localllm.harness.infrastructure.out.tools.FileTools;
import dev.localllm.harness.infrastructure.out.tools.HttpTools;
import dev.localllm.harness.infrastructure.out.tools.PlainTextResultConverter;
import dev.localllm.harness.infrastructure.out.tools.ShellTools;

/**
 * Reflection that Spring AOT does not register on its own, needed by the native image.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHintsConfig.Hints.class)
public class NativeHintsConfig {

  /**
   * Registers the hints.
   */
  static class Hints implements RuntimeHintsRegistrar {

    /** Hibernate Validator reads the components of the nested settings records. */
    private static final Class<?>[] VALIDATED_PROPERTIES = {
        HarnessProperties.class,
        HarnessProperties.Thinking.class,
        HarnessProperties.Context.class,
        HarnessProperties.Agent.class,
        HarnessProperties.Tools.class,
        HarnessProperties.Mcp.class,
    };

    /** Spring AI calls the {@code @Tool} methods and creates their result converter. */
    private static final Class<?>[] TOOLS = {
        FileTools.class, ShellTools.class, HttpTools.class, PlainTextResultConverter.class,
    };

    /** Read and written by Jackson. */
    private static final Class<?>[] JSON_TYPES = {
        JsonlLine.class, JsonlToolCall.class, OllamaRunningModels.class, McpServersFile.class,
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      for (Class<?> type : VALIDATED_PROPERTIES) {
        hints.reflection().registerType(type, MemberCategory.ACCESS_DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
      }
      for (Class<?> type : TOOLS) {
        hints.reflection().registerType(type,
            MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
      }
      new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), JSON_TYPES);
    }
  }
}
