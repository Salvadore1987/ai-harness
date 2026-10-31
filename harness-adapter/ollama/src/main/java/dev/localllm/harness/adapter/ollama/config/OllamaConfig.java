package dev.localllm.harness.adapter.ollama.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import dev.localllm.harness.adapter.ollama.dto.OllamaRunningModels;

/**
 * Binds the Ollama settings and registers the reflection the native image needs for them.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OllamaProperties.class)
@ImportRuntimeHints(OllamaConfig.OllamaNativeHints.class)
public class OllamaConfig {

  /**
   * Reflection that Spring AOT does not register on its own.
   */
  static class OllamaNativeHints implements RuntimeHintsRegistrar {

    /** Hibernate Validator reads the components of the nested settings records. */
    private static final Class<?>[] VALIDATED_PROPERTIES = {
        OllamaProperties.class,
        OllamaProperties.Context.class,
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      for (Class<?> type : VALIDATED_PROPERTIES) {
        hints.reflection().registerType(type, MemberCategory.ACCESS_DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
      }
      new BindingReflectionHintsRegistrar()
          .registerReflectionHints(hints.reflection(), OllamaRunningModels.class);
    }
  }
}
