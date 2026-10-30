package dev.localllm.harness.infrastructure.in.cli;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.ModelSummaryResult;
import dev.localllm.harness.application.dto.result.RunningModelResult;
import dev.localllm.harness.application.port.in.ListModelsQuery;
import dev.localllm.harness.application.port.in.ListRunningModelsQuery;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Prints the installed and the loaded models as tables; shared by the slash commands
 * {@code /list}, {@code /ps}, {@code /model} and the subcommands {@code harness list} and
 * {@code harness ps}.
 */
@Component
public class ModelListPrinter {

  private static final String INSTALLED_ROW = "%s%-32s %6s %-8s %s%n";
  private static final String RUNNING_ROW = "%-32s %8s %-14s %8s  %s%n";
  private static final String UNKNOWN = "—";
  private static final double GIGABYTE = 1e9;
  private static final double MEGABYTE = 1e6;
  private static final int PERCENT = 100;
  private static final int FOREVER_DAYS = 365;
  private static final int HOURS_SHOWN_UP_TO_DAYS = 2;

  private final ListModelsQuery listModelsQuery;
  private final ListRunningModelsQuery listRunningModelsQuery;
  private final ReplCliMapper mapper;
  private final Terminal terminal;
  private final Clock clock;

  /**
   * Creates the printer.
   *
   * @param listModelsQuery installed models
   * @param listRunningModelsQuery loaded models
   * @param mapper builds the list criteria
   * @param terminal terminal to print to
   * @param clock current time for the unload countdown
   */
  public ModelListPrinter(
      ListModelsQuery listModelsQuery,
      ListRunningModelsQuery listRunningModelsQuery,
      ReplCliMapper mapper,
      Terminal terminal,
      Clock clock) {
    this.listModelsQuery = listModelsQuery;
    this.listRunningModelsQuery = listRunningModelsQuery;
    this.mapper = mapper;
    this.terminal = terminal;
    this.clock = clock;
  }

  /**
   * Prints the installed models with their size and capabilities.
   *
   * @param namePrefix name prefix, empty for all models
   * @param current model marked with {@code *}
   */
  public void printInstalled(String namePrefix, String current) {
    List<ModelSummaryResult> models =
        listModelsQuery.execute(mapper.toListModelsCriteria(namePrefix));
    PrintWriter writer = terminal.writer();
    if (models.isEmpty()) {
      writer.println(namePrefix.isEmpty()
          ? "Нет установленных моделей. Выполните: ollama pull " + current
          : "Нет установленных моделей с префиксом " + namePrefix);
    } else {
      writer.printf(Locale.ROOT, INSTALLED_ROW, "  ", "МОДЕЛЬ", "ПАРАМ.", "КВАНТ.", "ВОЗМОЖНОСТИ");
      for (ModelSummaryResult model : models) {
        writer.printf(Locale.ROOT, INSTALLED_ROW, model.name().equals(current) ? "* " : "  ",
            model.name(), model.parameterSize(), model.quantization(), capabilities(model));
      }
    }
    writer.flush();
  }

  /**
   * Prints the models loaded into memory with their footprint, context and unload time.
   *
   * @param namePrefix name prefix, empty for all loaded models
   */
  public void printRunning(String namePrefix) {
    List<RunningModelResult> models =
        listRunningModelsQuery.execute(mapper.toListRunningModelsCriteria(namePrefix));
    PrintWriter writer = terminal.writer();
    if (models.isEmpty()) {
      writer.println("Нет загруженных моделей: Ollama загрузит модель при первом запросе");
    } else {
      writer.printf(Locale.ROOT, RUNNING_ROW,
          "МОДЕЛЬ", "РАЗМЕР", "ПРОЦЕССОР", "КОНТЕКСТ", "ВЫГРУЗКА");
      Instant now = clock.instant();
      for (RunningModelResult model : models) {
        writer.printf(Locale.ROOT, RUNNING_ROW, model.name(), size(model.sizeBytes()),
            processor(model.sizeBytes(), model.vramBytes()),
            model.contextLength() == null ? UNKNOWN : model.contextLength(),
            until(model.expiresAt(), now));
      }
    }
    writer.flush();
  }

  private static String capabilities(ModelSummaryResult model) {
    String thinking = model.thinkingSupported() ? "thinking" : "";
    String tools = model.toolsSupported() ? "tools" : "";
    return (thinking + " " + tools).strip();
  }

  static String size(long bytes) {
    if (bytes >= GIGABYTE) {
      return String.format(Locale.ROOT, bytes >= 10 * GIGABYTE ? "%.0f GB" : "%.1f GB",
          bytes / GIGABYTE);
    }
    return String.format(Locale.ROOT, "%.0f MB", bytes / MEGABYTE);
  }

  static String processor(long sizeBytes, long vramBytes) {
    if (sizeBytes <= 0) {
      return UNKNOWN;
    }
    long gpu = Math.round(vramBytes * (double) PERCENT / sizeBytes);
    if (gpu >= PERCENT) {
      return "100% GPU";
    }
    if (gpu <= 0) {
      return "100% CPU";
    }
    return (PERCENT - gpu) + "%/" + gpu + "% CPU/GPU";
  }

  static String until(@Nullable Instant expiresAt, Instant now) {
    if (expiresAt == null) {
      return UNKNOWN;
    }
    Duration left = Duration.between(now, expiresAt);
    if (left.isNegative() || left.isZero()) {
      return "выгружается";
    }
    if (left.toDays() >= FOREVER_DAYS) {
      return "бессрочно";
    }
    if (left.toMinutes() < 1) {
      return "меньше чем через минуту";
    }
    if (left.toHours() < 1) {
      return "через " + left.toMinutes() + " мин";
    }
    if (left.toDays() < HOURS_SHOWN_UP_TO_DAYS) {
      return "через " + left.toHours() + " ч " + left.toMinutesPart() + " мин";
    }
    return "через " + left.toDays() + " дн";
  }
}
