package dev.localllm.harness.infrastructure.in.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import dev.localllm.harness.application.dto.result.ToolSummaryResult;
import dev.localllm.harness.application.port.in.ListToolsQuery;
import dev.localllm.harness.infrastructure.in.cli.mapper.ReplCliMapper;

/**
 * Slash commands about the agent: {@code /mode} and {@code /tools}.
 */
@Component
public class AgentSlashCommands {

  private static final int DESCRIPTION_LENGTH = 90;

  private final ListToolsQuery listToolsQuery;
  private final ReplCliMapper mapper;
  private final Terminal terminal;

  /**
   * Creates the commands.
   *
   * @param listToolsQuery lists the tools for {@code /tools}
   * @param mapper builds queries from the CLI state
   * @param terminal terminal to print to
   */
  public AgentSlashCommands(ListToolsQuery listToolsQuery, ReplCliMapper mapper,
      Terminal terminal) {
    this.listToolsQuery = listToolsQuery;
    this.mapper = mapper;
    this.terminal = terminal;
  }

  /**
   * {@code /mode [chat|agent]}: shows or changes how messages are handled.
   *
   * @param session CLI state
   * @param argument {@code chat}, {@code agent} or empty to show the mode
   */
  public void mode(ReplSession session, String argument) {
    if (argument.isEmpty()) {
      println("Режим: " + name(session.getMode()));
      return;
    }
    ReplMode requested;
    try {
      requested = ReplMode.valueOf(argument.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      println("Использование: /mode chat|agent");
      return;
    }
    if (requested == ReplMode.AGENT && !session.isToolsSupported()) {
      println("Модель " + session.getModel() + " не поддерживает инструменты; режим agent "
          + "недоступен. Выберите модель с пометкой tools: /model");
      return;
    }
    session.setMode(requested);
    println("Режим: " + name(requested));
  }

  /**
   * {@code /tools [prefix]}: lists the tools available to the agent.
   *
   * @param argument name prefix, empty for all tools
   */
  public void tools(String argument) {
    List<ToolSummaryResult> tools = listToolsQuery.execute(mapper.toListToolsCriteria(argument));
    if (tools.isEmpty()) {
      println(argument.isEmpty() ? "Инструментов нет" : "Нет инструментов на " + argument);
      return;
    }
    int nameWidth = tools.stream().mapToInt(tool -> tool.name().length()).max().orElse(0);
    int sourceWidth = tools.stream().mapToInt(tool -> source(tool).length()).max().orElse(0);
    String format = "%-" + nameWidth + "s  %-" + sourceWidth + "s  %s%n";
    PrintWriter writer = terminal.writer();
    for (ToolSummaryResult tool : tools) {
      writer.printf(Locale.ROOT, format,
          tool.name(), source(tool), firstSentence(tool.description()));
    }
    writer.flush();
  }

  private static String source(ToolSummaryResult tool) {
    String server = tool.server();
    return server == null ? tool.source().name().toLowerCase(Locale.ROOT) : server;
  }

  private static String name(ReplMode mode) {
    return mode.name().toLowerCase(Locale.ROOT);
  }

  private static String firstSentence(String description) {
    String line = description.strip().lines().findFirst().orElse("");
    int end = line.indexOf(". ");
    String sentence = end < 0 ? line : line.substring(0, end + 1);
    return sentence.length() <= DESCRIPTION_LENGTH
        ? sentence : sentence.substring(0, DESCRIPTION_LENGTH - 1) + "…";
  }

  private void println(String text) {
    terminal.writer().println(text);
    terminal.writer().flush();
  }
}
