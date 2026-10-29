package dev.localllm.harness.application.service;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import dev.localllm.harness.application.dto.command.SwitchModelCommand;
import dev.localllm.harness.application.dto.command.ToggleThinkingCommand;
import dev.localllm.harness.application.dto.query.ListModelsCriteria;
import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.ThinkingToggleResult;
import dev.localllm.harness.application.exception.ModelNotFoundException;
import dev.localllm.harness.application.exception.ThinkingNotSupportedException;
import dev.localllm.harness.application.fixture.FakeModelCatalogPort;
import dev.localllm.harness.application.mapper.TurnApplicationMapper;
import dev.localllm.harness.application.model.ModelInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelServicesTest {

  private final TurnApplicationMapper mapper = Mappers.getMapper(TurnApplicationMapper.class);
  private final FakeModelCatalogPort catalog = new FakeModelCatalogPort(
      FakeModelCatalogPort.QWEN, FakeModelCatalogPort.CODELLAMA);

  @Test
  void switchModel_WhenInstalled_ReturnsSummaryWithCapabilities() {
    // Arrange
    SwitchModelService service = new SwitchModelService(catalog, mapper);

    // Act
    ModelSummaryResult result = service.execute(new SwitchModelCommand("qwen3.5:27b"));

    // Assert
    assertThat(result).isEqualTo(
        new ModelSummaryResult("qwen3.5:27b", "27B", "Q4_K_M", true, true));
  }

  @Test
  void switchModel_WhenMissing_ThrowsModelNotFound() {
    // Arrange
    SwitchModelService service = new SwitchModelService(catalog, mapper);

    // Act & Assert
    assertThatThrownBy(() -> service.execute(new SwitchModelCommand("llama9:1t")))
        .isInstanceOf(ModelNotFoundException.class)
        .hasMessageContaining("llama9:1t");
  }

  @Test
  void toggleThinking_EnableOnThinkingModel_ReturnsEnabled() {
    // Arrange
    ToggleThinkingService service = new ToggleThinkingService(catalog, mapper);

    // Act
    ThinkingToggleResult result = service.execute(new ToggleThinkingCommand("qwen3.5:27b", true));

    // Assert
    assertThat(result.enabled()).isTrue();
  }

  @Test
  void toggleThinking_EnableOnModelWithoutThinking_ThrowsNotSupported() {
    // Arrange
    ToggleThinkingService service = new ToggleThinkingService(catalog, mapper);
    ToggleThinkingCommand command = new ToggleThinkingCommand("codellama:7b-instruct", true);

    // Act & Assert
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(ThinkingNotSupportedException.class);
  }

  @Test
  void toggleThinking_EnableOnUnknownModel_ThrowsModelNotFound() {
    // Arrange
    ToggleThinkingService service = new ToggleThinkingService(catalog, mapper);

    // Act & Assert
    assertThatThrownBy(() -> service.execute(new ToggleThinkingCommand("missing", true)))
        .isInstanceOf(ModelNotFoundException.class);
  }

  @Test
  void toggleThinking_Disable_ReturnsDisabledForAnyModel() {
    // Arrange
    ToggleThinkingService service = new ToggleThinkingService(catalog, mapper);

    // Act
    ThinkingToggleResult result = service.execute(new ToggleThinkingCommand("missing", false));

    // Assert
    assertThat(result.enabled()).isFalse();
  }

  @Test
  void listModels_WithPrefix_ReturnsMatchingModelsSortedByName() {
    // Arrange
    ListModelsQueryService service = new ListModelsQueryService(
        new FakeModelCatalogPort(
            FakeModelCatalogPort.QWEN,
            FakeModelCatalogPort.CODELLAMA,
            new ModelInfo("qwen2.5-coder:7b", "7B", "Q4_K_M", Set.of())),
        mapper);

    // Act
    List<ModelSummaryResult> result = service.execute(new ListModelsCriteria("qwen"));

    // Assert
    assertThat(result).extracting(ModelSummaryResult::name)
        .containsExactly("qwen2.5-coder:7b", "qwen3.5:27b");
  }
}
