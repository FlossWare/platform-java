/*
 * Copyright (C) 2024-2026 FlossWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
 *
 * parseArguments("--message \"He said \\\"hello\\\"\"")
 * // Returns: ["--message", "He said \"hello\""]
 * }</pre>
 *
 * <p>Thread-safe and stateless.</p>
 */
class ArgumentParser {

    // Pattern that handles escaped quotes: \" and \' within quoted strings
    private static final Pattern ARG_PATTERN = Pattern.compile(
        "\"((?:[^\\\\\"]|\\\\.)*)\"|'((?:[^\\\\']|\\\\.)*)'|(\\S+)");

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
                // Double-quoted string - unescape backslash sequences
                args.add(unescapeString(matcher.group(1)));
            } else if (matcher.group(2) != null) {
                // Single-quoted string - unescape backslash sequences
                args.add(unescapeString(matcher.group(2)));
            } else if (matcher.group(3) != null) {
                // Unquoted token
                args.add(matcher.group(3));
            }
        }

        return args;
    }

    /**
     * Unescapes backslash sequences in a string.
     * Converts \" to ", \' to ', \\ to \, etc.
     *
     * @param str the string to unescape
     * @return the unescaped string
     */
    private static String unescapeString(String str) {
        if (str == null || !str.contains("\\")) {
            return str;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                // Unescape the next character
                char next = str.charAt(i + 1);
                switch (next) {
                    case 'n':
                        result.append('\n');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case '\\':
                    case '"':
                    case '\'':
                        result.append(next);
                        break;
                    default:
                        // Unknown escape sequence, keep the backslash
                        result.append('\\').append(next);
                        break;
                }
                i++; // Skip the next character
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
