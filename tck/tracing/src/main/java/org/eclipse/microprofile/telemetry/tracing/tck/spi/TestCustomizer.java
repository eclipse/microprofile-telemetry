/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 *  See the NOTICE file(s) distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.eclipse.microprofile.telemetry.tracing.tck.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

public class TestCustomizer implements AutoConfigurationCustomizerProvider {

    public static final AttributeKey<String> TEST_KEY = AttributeKey.stringKey("test-key");
    public static final String TEST_VALUE = "test-value";

    public static final List<String> LOGGED_EVENTS = new ArrayList<>();

    /** {@inheritDoc} */
    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
        // Do an actual customization of the resource
        autoConfiguration.addResourceCustomizer((r, c) -> r.toBuilder().put(TEST_KEY, TEST_VALUE).build());

        // Just check that we can add the other customizers that relate to tracing and that they get called
        // Use actual methods so that we know we're really loading every class and not missing some due to generic
        // erasure
        autoConfiguration.addPropagatorCustomizer(this::customizePropagator);
        autoConfiguration.addPropertiesCustomizer(this::customizeProperties);
        autoConfiguration.addSamplerCustomizer(this::customizeSampler);
        autoConfiguration.addSpanExporterCustomizer(this::customizeExporter);
        autoConfiguration.addTracerProviderCustomizer(this::customizeTracer);
    }

    private TextMapPropagator customizePropagator(TextMapPropagator propagator, ConfigProperties config) {
        LOGGED_EVENTS.add("propagator");
        return propagator;
    }

    private Map<String, String> customizeProperties(ConfigProperties config) {
        LOGGED_EVENTS.add("properties");
        return Collections.emptyMap();
    }

    private Sampler customizeSampler(Sampler sampler, ConfigProperties config) {
        LOGGED_EVENTS.add("sampler");
        return sampler;
    }

    private SpanExporter customizeExporter(SpanExporter exporter, ConfigProperties config) {
        LOGGED_EVENTS.add("exporter");
        return exporter;
    }

    private SdkTracerProviderBuilder customizeTracer(SdkTracerProviderBuilder tracerBuilder, ConfigProperties config) {
        LOGGED_EVENTS.add("tracer");
        return tracerBuilder;
    }

}
