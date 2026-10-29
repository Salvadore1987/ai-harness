package dev.localllm.harness.infrastructure.in.cli;

import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * Completes slash command names in the first word of the input.
 */
public final class SlashCommandCompleter implements Completer {

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    if (line.wordIndex() != 0 || !line.word().startsWith("/")) {
      return;
    }
    for (String command : SlashCommandDispatcher.COMMANDS) {
      candidates.add(new Candidate(command));
    }
  }
}
