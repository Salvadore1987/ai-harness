package dev.localllm.harness.application.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.SwitchProviderCommand;
import dev.localllm.harness.application.dto.query.ListProvidersCriteria;
import dev.localllm.harness.application.dto.result.ProviderResult;
import dev.localllm.harness.application.dto.result.ProviderSummaryResult;
import dev.localllm.harness.application.exception.ProviderUnavailableException;
import dev.localllm.harness.application.fixture.FakeProviderCatalogPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.LlmProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderServicesTest {

  private final TurnApplicationMapper mapper = Mappers.getMapper(TurnApplicationMapper.class);

  @Test
  void switchProvider_WhenAvailable_ReturnsItsDefaultModel() {
    // Arrange
    SwitchProviderService service = new SwitchProviderService(new FakeProviderCatalogPort(
        FakeProviderCatalogPort.OLLAMA, FakeProviderCatalogPort.ANTHROPIC), mapper);

    // Act
    ProviderResult result = service.execute(new SwitchProviderCommand(LlmProvider.ANTHROPIC));

    // Assert
    assertThat(result).isEqualTo(new ProviderResult(LlmProvider.ANTHROPIC, "claude-opus-5-5"));
  }

  @Test
  void switchProvider_WhenUnavailable_ThrowsProviderUnavailable() {
    // Arrange
    SwitchProviderService service = new SwitchProviderService(new FakeProviderCatalogPort(
        FakeProviderCatalogPort.OLLAMA, FakeProviderCatalogPort.ANTHROPIC_UNAVAILABLE), mapper);
    SwitchProviderCommand command = new SwitchProviderCommand(LlmProvider.ANTHROPIC);

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(ProviderUnavailableException.class)
        .hasMessageContaining("ANTHROPIC")
        .extracting("code").isEqualTo(ProviderUnavailableException.CODE);
  }

  @Test
  void switchProvider_WhenNotInCatalog_ThrowsProviderUnavailable() {
    // Arrange
    SwitchProviderService service = new SwitchProviderService(
        new FakeProviderCatalogPort(FakeProviderCatalogPort.OLLAMA), mapper);
    SwitchProviderCommand command = new SwitchProviderCommand(LlmProvider.ANTHROPIC);

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(ProviderUnavailableException.class);
  }

  @Test
  void listProviders_InAnyCatalogOrder_ReturnsDeclarationOrderWithActiveMarked() {
    // Arrange
    ListProvidersQueryService service = new ListProvidersQueryService(new FakeProviderCatalogPort(
        FakeProviderCatalogPort.ANTHROPIC_UNAVAILABLE, FakeProviderCatalogPort.OLLAMA), mapper);

    // Act
    List<ProviderSummaryResult> result =
        service.execute(new ListProvidersCriteria(LlmProvider.OLLAMA));

    // Assert
    assertThat(result).containsExactly(
        new ProviderSummaryResult(LlmProvider.OLLAMA, true, true, "qwen3.5:27b"),
        new ProviderSummaryResult(LlmProvider.ANTHROPIC, false, false, "claude-opus-5-5"));
  }
}
