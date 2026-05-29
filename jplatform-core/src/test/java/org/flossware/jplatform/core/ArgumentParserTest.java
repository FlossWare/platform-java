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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ArgumentParser.
 * Tests command-line argument parsing with various quote scenarios.
 */
class ArgumentParserTest {

    @Test
    void testParseArgumentsNull() {
        List<String> result = ArgumentParser.parseArguments(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseArgumentsEmpty() {
        List<String> result = ArgumentParser.parseArguments("");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseArgumentsWhitespaceOnly() {
        List<String> result = ArgumentParser.parseArguments("   \t  \n  ");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseArgumentsSimpleTokens() {
        List<String> result = ArgumentParser.parseArguments("--config file.json --port 8080");

        assertEquals(4, result.size());
        assertEquals("--config", result.get(0));
        assertEquals("file.json", result.get(1));
        assertEquals("--port", result.get(2));
        assertEquals("8080", result.get(3));
    }

    @Test
    void testParseArgumentsDoubleQuotedWithSpaces() {
        List<String> result = ArgumentParser.parseArguments("--config \"/path with spaces/config.json\" --port 8080");

        assertEquals(4, result.size());
        assertEquals("--config", result.get(0));
        assertEquals("/path with spaces/config.json", result.get(1));
        assertEquals("--port", result.get(2));
        assertEquals("8080", result.get(3));
    }

    @Test
    void testParseArgumentsSingleQuotedWithSpaces() {
        List<String> result = ArgumentParser.parseArguments("--config '/path with spaces/config.json' --port 8080");

        assertEquals(4, result.size());
        assertEquals("--config", result.get(0));
        assertEquals("/path with spaces/config.json", result.get(1));
        assertEquals("--port", result.get(2));
        assertEquals("8080", result.get(3));
    }

    @Test
    void testParseArgumentsEscapedDoubleQuotes() {
        List<String> result = ArgumentParser.parseArguments("--message \"He said \\\"hello\\\"\"");

        assertEquals(2, result.size());
        assertEquals("--message", result.get(0));
        assertEquals("He said \"hello\"", result.get(1));
    }

    @Test
    void testParseArgumentsEscapedSingleQuotes() {
        List<String> result = ArgumentParser.parseArguments("--message 'It\\'s working'");

        assertEquals(2, result.size());
        assertEquals("--message", result.get(0));
        assertEquals("It's working", result.get(1));
    }

    @Test
    void testParseArgumentsEscapedBackslash() {
        List<String> result = ArgumentParser.parseArguments("--path \"C:\\\\Windows\\\\System32\"");

        assertEquals(2, result.size());
        assertEquals("--path", result.get(0));
        assertEquals("C:\\Windows\\System32", result.get(1));
    }

    @Test
    void testParseArgumentsNewlineEscape() {
        List<String> result = ArgumentParser.parseArguments("--message \"Line1\\nLine2\"");

        assertEquals(2, result.size());
        assertEquals("--message", result.get(0));
        assertEquals("Line1\nLine2", result.get(1));
    }

    @Test
    void testParseArgumentsTabEscape() {
        List<String> result = ArgumentParser.parseArguments("--message \"Col1\\tCol2\"");

        assertEquals(2, result.size());
        assertEquals("--message", result.get(0));
        assertEquals("Col1\tCol2", result.get(1));
    }

    @Test
    void testParseArgumentsCarriageReturnEscape() {
        List<String> result = ArgumentParser.parseArguments("--message \"Before\\rAfter\"");

        assertEquals(2, result.size());
        assertEquals("--message", result.get(0));
        assertEquals("Before\rAfter", result.get(1));
    }

    @Test
    void testParseArgumentsUnknownEscapeSequence() {
        // Unknown escape sequences should preserve the backslash
        List<String> result = ArgumentParser.parseArguments("--message \"test\\xvalue\"");

        assertEquals(2, result.size());
        assertEquals("--message", result.get(0));
        assertEquals("test\\xvalue", result.get(1));
    }

    @Test
    void testParseArgumentsMixedQuotedAndUnquoted() {
        List<String> result = ArgumentParser.parseArguments("--file \"my file.txt\" --verbose --output dir/out.log");

        assertEquals(5, result.size());
        assertEquals("--file", result.get(0));
        assertEquals("my file.txt", result.get(1));
        assertEquals("--verbose", result.get(2));
        assertEquals("--output", result.get(3));
        assertEquals("dir/out.log", result.get(4));
    }

    @Test
    void testParseArgumentsEmptyQuotedString() {
        List<String> result = ArgumentParser.parseArguments("--name \"\" --value ''");

        assertEquals(4, result.size());
        assertEquals("--name", result.get(0));
        assertEquals("", result.get(1));
        assertEquals("--value", result.get(2));
        assertEquals("", result.get(3));
    }

    @Test
    void testParseArgumentsOnlyQuotedStrings() {
        List<String> result = ArgumentParser.parseArguments("\"first arg\" 'second arg' \"third arg\"");

        assertEquals(3, result.size());
        assertEquals("first arg", result.get(0));
        assertEquals("second arg", result.get(1));
        assertEquals("third arg", result.get(2));
    }

    @Test
    void testParseArgumentsComplexEscaping() {
        List<String> result = ArgumentParser.parseArguments("--json \"{\\\"key\\\":\\\"value\\\"}\"");

        assertEquals(2, result.size());
        assertEquals("--json", result.get(0));
        assertEquals("{\"key\":\"value\"}", result.get(1));
    }

    @Test
    void testParseArgumentsMultipleEscapeSequences() {
        List<String> result = ArgumentParser.parseArguments("--text \"Line1\\nLine2\\tCol\\\\Backslash\\\"Quote\"");

        assertEquals(2, result.size());
        assertEquals("--text", result.get(0));
        assertEquals("Line1\nLine2\tCol\\Backslash\"Quote", result.get(1));
    }

    @Test
    void testParseArgumentsTrailingBackslashInQuote() {
        // Backslash at end of string should be preserved
        List<String> result = ArgumentParser.parseArguments("--path \"C:\\\\Users\\\\\"");

        assertEquals(2, result.size());
        assertEquals("--path", result.get(0));
        // Trailing backslash is preserved as is
        assertEquals("C:\\Users\\", result.get(1));
    }

    @Test
    void testParseArgumentsSingleToken() {
        List<String> result = ArgumentParser.parseArguments("standalone");

        assertEquals(1, result.size());
        assertEquals("standalone", result.get(0));
    }

    @Test
    void testParseArgumentsSingleQuotedToken() {
        List<String> result = ArgumentParser.parseArguments("\"single quoted token with spaces\"");

        assertEquals(1, result.size());
        assertEquals("single quoted token with spaces", result.get(0));
    }

    @Test
    void testParseArgumentsSpecialCharactersUnquoted() {
        List<String> result = ArgumentParser.parseArguments("--file=/path/to/file.txt --port=8080");

        assertEquals(2, result.size());
        assertEquals("--file=/path/to/file.txt", result.get(0));
        assertEquals("--port=8080", result.get(1));
    }

    @Test
    void testParseArgumentsMultipleSpacesBetweenTokens() {
        List<String> result = ArgumentParser.parseArguments("--config    file.json     --port   8080");

        assertEquals(4, result.size());
        assertEquals("--config", result.get(0));
        assertEquals("file.json", result.get(1));
        assertEquals("--port", result.get(2));
        assertEquals("8080", result.get(3));
    }

    @Test
    void testParseArgumentsMixedDoubleAndSingleQuotes() {
        List<String> result = ArgumentParser.parseArguments("\"double quoted\" 'single quoted' unquoted");

        assertEquals(3, result.size());
        assertEquals("double quoted", result.get(0));
        assertEquals("single quoted", result.get(1));
        assertEquals("unquoted", result.get(2));
    }

    @Test
    void testParseArgumentsRealWorldExample() {
        String command = "java -jar app.jar --config \"/etc/myapp/config.json\" --log-level INFO --data-dir \"/var/lib/myapp data\"";
        List<String> result = ArgumentParser.parseArguments(command);

        assertEquals(9, result.size());
        assertEquals("java", result.get(0));
        assertEquals("-jar", result.get(1));
        assertEquals("app.jar", result.get(2));
        assertEquals("--config", result.get(3));
        assertEquals("/etc/myapp/config.json", result.get(4));
        assertEquals("--log-level", result.get(5));
        assertEquals("INFO", result.get(6));
        assertEquals("--data-dir", result.get(7));
        assertEquals("/var/lib/myapp data", result.get(8));
    }
}
