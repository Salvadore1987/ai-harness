package dev.localllm.harness.adapter.anthropic.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Binds the Anthropic settings, creates the Claude API client and registers the reflection the
 * native image needs for them.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnthropicProperties.class)
@ImportRuntimeHints(AnthropicConfig.AnthropicNativeHints.class)
public class AnthropicConfig {

  /**
   * Client of the Claude API. It is built from the settings only, not from the environment of
   * the SDK, so an empty key stays empty and the provider stays unavailable.
   *
   * @param properties Anthropic settings with the key and the address
   * @return client, closed with the context
   */
  @Bean
  public AnthropicClient anthropicClient(AnthropicProperties properties) {
    return AnthropicOkHttpClient.builder()
        .apiKey(properties.apiKey())
        .baseUrl(properties.baseUrl())
        .build();
  }

  /**
   * Reflection that Spring AOT does not register on its own.
   */
  static class AnthropicNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      // Hibernate Validator reads the components of the validated settings record.
      hints.reflection().registerType(AnthropicProperties.class,
          MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
          MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
  }
}
