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

package org.flossware.jplatform.api;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Parser for loading ApplicationDescriptor from external configuration formats.
 * Implementations provide support for different formats (YAML, JSON, XML).
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ApplicationDescriptorParser parser = new YamlDescriptorParser();
 * ApplicationDescriptor descriptor = parser.parseFile(Paths.get("app.yaml"));
 * applicationManager.deploy(descriptor);
 * }</pre>
 *
 * @see ApplicationDescriptor
 */
public interface ApplicationDescriptorParser {

    /**
     * Parses an ApplicationDescriptor from an input stream.
     *
     * @param input the input stream containing the descriptor content
     * @return the parsed ApplicationDescriptor
     * @throws ParseException if parsing fails
     */
    ApplicationDescriptor parse(InputStream input) throws ParseException;

    /**
     * Parses an ApplicationDescriptor from a file.
     *
     * @param file the file containing the descriptor
     * @return the parsed ApplicationDescriptor
     * @throws ParseException if parsing fails or file cannot be read
     */
    ApplicationDescriptor parseFile(Path file) throws ParseException;

    /**
     * Returns the format supported by this parser.
     *
     * @return the supported format
     */
    Format getSupportedFormat();

    /**
     * Supported configuration formats.
     */
    enum Format {
        YAML, JSON, XML
    }
}
