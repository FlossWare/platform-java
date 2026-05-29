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

package org.flossware.jplatform.cluster.consul;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.flossware.jplatform.api.ApplicationDescriptor;
import org.flossware.jplatform.api.ThreadPoolConfig;

import java.io.IOException;
import java.net.URI;

/**
 * Jackson module for serializing/deserializing ApplicationDescriptor.
 * Handles the Builder pattern and excludes non-serializable fields like SecurityConfig.
 */
public class ApplicationDescriptorJsonModule extends SimpleModule {

    public ApplicationDescriptorJsonModule() {
        super("ApplicationDescriptorModule");
        addSerializer(ApplicationDescriptor.class, new ApplicationDescriptorSerializer());
        addDeserializer(ApplicationDescriptor.class, new ApplicationDescriptorDeserializer());
    }

    /**
     * Custom serializer for ApplicationDescriptor.
     * Excludes SecurityConfig to avoid FilePermission serialization issues.
     */
    private static class ApplicationDescriptorSerializer extends JsonSerializer<ApplicationDescriptor> {
        @Override
        public void serialize(ApplicationDescriptor descriptor, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("applicationId", descriptor.getApplicationId());
            gen.writeStringField("name", descriptor.getName());
            gen.writeStringField("version", descriptor.getVersion());
            gen.writeStringField("mainClass", descriptor.getMainClass());

            // Write classpath
            gen.writeArrayFieldStart("classpath");
            for (URI uri : descriptor.getClasspathEntries()) {
                gen.writeString(uri.toString());
            }
            gen.writeEndArray();

            // Write properties
            gen.writeObjectField("properties", descriptor.getProperties());

            // Write thread pool config if present
            if (descriptor.getThreadPoolConfig() != null) {
                gen.writeObjectFieldStart("threadPool");
                ThreadPoolConfig config = descriptor.getThreadPoolConfig();
                gen.writeNumberField("corePoolSize", config.getCorePoolSize());
                gen.writeNumberField("maxPoolSize", config.getMaxPoolSize());
                gen.writeNumberField("queueCapacity", config.getQueueCapacity());
                gen.writeNumberField("keepAliveTimeSeconds", config.getKeepAliveTimeSeconds());
                gen.writeEndObject();
            }

            // Write messaging flag
            gen.writeBooleanField("enableMessaging", descriptor.isEnableMessaging());

            gen.writeEndObject();
        }
    }

    /**
     * Custom deserializer for ApplicationDescriptor.
     * Reconstructs using the Builder pattern.
     */
    private static class ApplicationDescriptorDeserializer extends JsonDeserializer<ApplicationDescriptor> {
        @Override
        public ApplicationDescriptor deserialize(JsonParser parser, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);

            ApplicationDescriptor.Builder builder = ApplicationDescriptor.builder()
                    .applicationId(node.get("applicationId").asText())
                    .name(node.get("name").asText())
                    .version(node.get("version").asText())
                    .mainClass(node.get("mainClass").asText());

            // Add classpath entries
            JsonNode classpath = node.get("classpath");
            if (classpath != null && classpath.isArray()) {
                for (JsonNode entry : classpath) {
                    builder.addClasspathEntry(URI.create(entry.asText()));
                }
            }

            // Add properties
            JsonNode props = node.get("properties");
            if (props != null && props.isObject()) {
                props.fields().forEachRemaining(entry ->
                        builder.property(entry.getKey(), entry.getValue().asText()));
            }

            // Add thread pool config if present
            JsonNode threadPool = node.get("threadPool");
            if (threadPool != null) {
                ThreadPoolConfig config = ThreadPoolConfig.builder()
                        .corePoolSize(threadPool.get("corePoolSize").asInt())
                        .maxPoolSize(threadPool.get("maxPoolSize").asInt())
                        .queueCapacity(threadPool.get("queueCapacity").asInt())
                        .keepAliveTimeSeconds(threadPool.get("keepAliveTimeSeconds").asLong())
                        .build();
                builder.threadPoolConfig(config);
            }

            // Set messaging flag
            JsonNode enableMessaging = node.get("enableMessaging");
            if (enableMessaging != null) {
                builder.enableMessaging(enableMessaging.asBoolean());
            }

            return builder.build();
        }
    }
}
