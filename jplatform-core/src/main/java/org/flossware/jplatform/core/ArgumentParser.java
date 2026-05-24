package org.flossware.jplatform.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing command-line arguments with proper quote handling.
 *
 * <p>Handles quoted strings, allowing arguments to contain spaces:</p>
 * <pre>{@code
 * parseArguments("--config \"/path with spaces/config.json\" --port 8080")
 * // Returns: ["--config", "/path with spaces/config.json", "--port", "8080"]
 * }</pre>
 *
 * <p>Thread-safe and stateless.</p>
 */
class ArgumentParser {

    private static final Pattern ARG_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    /**
     * Parses a command-line argument string respecting quoted strings.
     *
     * <p>Supports both double quotes ("...") and single quotes ('...').
     * Quoted strings can contain spaces, which will be preserved as single arguments.
     * Unquoted sequences are split by whitespace.</p>
     *
     * @param argsString the argument string to parse (e.g., "--file \"my file.txt\" --verbose")
     * @return list of parsed arguments with quotes removed
     */
    static List<String> parseArguments(String argsString) {
        if (argsString == null || argsString.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> args = new ArrayList<>();
        Matcher matcher = ARG_PATTERN.matcher(argsString);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Double-quoted string
                args.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                // Single-quoted string
                args.add(matcher.group(2));
            } else if (matcher.group(3) != null) {
                // Unquoted token
                args.add(matcher.group(3));
            }
        }

        return args;
    }
}
