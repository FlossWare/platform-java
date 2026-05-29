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

package org.flossware.jplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ApplicationDescriptorParser;
import org.flossware.jplatform.api.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Abstract base class for ApplicationDescriptor parsers using Jackson.
 * Provides common parsing logic and error handling, delegating format-specific
 * ObjectMapper creation to subclasses.
 *
 * <p>Subclasses must implement {@link #createObjectMapper()} to provide the
 * appropriate ObjectMapper for their format (YAML, JSON, etc.).</p>
 *
 * <p>This class handles:</p>
 * <ul>
 *   <li>Common validation logic</li>
 *   <li>Exception handling and wrapping in ParseException</li>
 *   <li>File I/O using {@link #parseFile(Path)}</li>
 *   <li>Stream-based parsing using {@link #parse(InputStream)}</li>
 * </ul>
 *
 * <p>Example subclass implementation:</p>
 * <pre>{@code
 * public class YamlDescriptorParser extends AbstractDescriptorParser {
 *     @Override
 *     protected ObjectMapper createObjectMapper() {
 *         return new YAMLMapper();
 *     }
 *
 *     @Override
 *     public Format getSupportedFormat() {
 *         return Format.YAML;
 *     }
 * }
 * }</pre>
 *
 * @see ApplicationDescriptorParser
 * @see ApplicationDescriptorDTO
 */
public abstract class AbstractDescriptorParser implements ApplicationDescriptorParser {

    private static final Logger logger = LoggerFactory.getLogger(AbstractDescriptorParser.class);

    private final ObjectMapper objectMapper;

    /**
     * Constructs a new parser with an ObjectMapper created by {@link #createObjectMapper()}.
     */
    protected AbstractDescriptorParser() {
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates the ObjectMapper for parsing the specific format.
     * Subclasses must implement this to provide the appropriate mapper
     * (e.g., YAMLMapper for YAML, standard ObjectMapper for JSON).
     *
     * @return the ObjectMapper configured for this format
     */
    protected abstract ObjectMapper createObjectMapper();

    /**
     * Parses an ApplicationDescriptor from an input stream.
     *
     * @param input the input stream containing the descriptor content
     * @return the parsed ApplicationDescriptor
     * @throws ParseException if parsing fails or validation fails
     */
    @Override
    public ApplicationDescriptor parse(InputStream input) throws ParseException {
        Objects.requireNonNull(input, "input stream cannot be null");

        logger.debug("Parsing ApplicationDescriptor from input stream using {} format", getSupportedFormat());

        try {
            ApplicationDescriptorDTO dto = objectMapper.readValue(input, ApplicationDescriptorDTO.class);

            validateDTO(dto);

            ApplicationDescriptor descriptor = dto.toApplicationDescriptor();

            logger.info("Successfully parsed ApplicationDescriptor: applicationId={}, mainClass={}",
                    descriptor.getApplicationId(), descriptor.getMainClass());

            return descriptor;
        } catch (IOException e) {
            String message = String.format("Failed to parse %s descriptor: %s",
                    getSupportedFormat(), e.getMessage());
            logger.error(message, e);
            throw new ParseException(message, e);
        }
    }

    /**
     * Parses an ApplicationDescriptor from a file.
     *
     * @param file the file containing the descriptor
     * @return the parsed ApplicationDescriptor
     * @throws ParseException if parsing fails, file cannot be read, or validation fails
     */
    @Override
    public ApplicationDescriptor parseFile(Path file) throws ParseException {
        Objects.requireNonNull(file, "file path cannot be null");

        logger.debug("Parsing ApplicationDescriptor from file: {}", file);

        if (!Files.exists(file)) {
            throw new ParseException("File does not exist: " + file);
        }

        if (!Files.isRegularFile(file)) {
            throw new ParseException("Path is not a regular file: " + file);
        }

        if (!Files.isReadable(file)) {
            throw new ParseException("File is not readable: " + file);
        }

        try (InputStream input = Files.newInputStream(file)) {
            return parse(input);
        } catch (IOException e) {
            String message = String.format("Failed to read file %s: %s", file, e.getMessage());
            logger.error(message, e);
            throw new ParseException(message, e);
        }
    }

    /**
     * Validates the DTO after deserialization but before conversion to domain object.
     *
     * @param dto the DTO to validate
     * @throws ParseException if validation fails
     */
    protected void validateDTO(ApplicationDescriptorDTO dto) throws ParseException {
        if (dto == null) {
            throw new ParseException("Parsed DTO is null");
        }

        // Validate required fields
        if (dto.getApplicationId() == null || dto.getApplicationId().trim().isEmpty()) {
            throw new ParseException("applicationId is required");
        }
        if (dto.getMainClass() == null || dto.getMainClass().trim().isEmpty()) {
            throw new ParseException("mainClass is required");
        }

        logger.debug("DTO validation passed");
    }

    /**
     * Returns the ObjectMapper used by this parser.
     * Exposed for testing purposes.
     *
     * @return the ObjectMapper instance
     */
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
