package dev.localllm.harness.infrastructure.in.cli;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelListPrinterTest {

  private static final Instant NOW = Instant.parse("2026-10-29T16:30:00Z");

  @ParameterizedTest
  @CsvSource({
      "19744124896, 20 GB",
      "5137025024, 5.1 GB",
      "734003200, 734 MB",
  })
  void size_FormatsDecimalUnitsLikeOllama(long bytes, String expected) {
    // Act
    String size = ModelListPrinter.size(bytes);

    // Assert
    assertThat(size).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
      "100, 100, 100% GPU",
      "100, 0, 100% CPU",
      "100, 52, 48%/52% CPU/GPU",
      "0, 0, —",
  })
  void processor_SplitsMemoryBetweenCpuAndGpu(long size, long vram, String expected) {
    // Act
    String processor = ModelListPrinter.processor(size, vram);

    // Assert
    assertThat(processor).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
      "PT-1S, выгружается",
      "PT30S, меньше чем через минуту",
      "PT29M30S, через 29 мин",
      "PT3H15M, через 3 ч 15 мин",
      "P3D, через 3 дн",
      "P100000D, бессрочно",
  })
  void until_DescribesTimeLeftBeforeUnload(Duration left, String expected) {
    // Act
    String until = ModelListPrinter.until(NOW.plus(left), NOW);

    // Assert
    assertThat(until).isEqualTo(expected);
  }

  @Test
  void until_WhenServerDidNotReport_ReturnsDash() {
    // Act
    String until = ModelListPrinter.until(null, NOW);

    // Assert
    assertThat(until).isEqualTo("—");
  }
}
