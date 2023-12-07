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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.annotations.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;

public class CustomizerSpiTest extends Arquillian {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, TestCustomizer.class)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsServiceProvider(AutoConfigurationCustomizerProvider.class, TestCustomizer.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsResource(new StringAsset("otel.sdk.disabled=false\notel.traces.exporter=in-memory"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

    }

    @Inject
    private Tracer tracer;

    @Inject
    private InMemorySpanExporter exporter;

    @Test
    public void testCustomizer() {
        Span span = tracer.spanBuilder("span").startSpan();
        span.end();

        List<SpanData> spanItems = exporter.getFinishedSpanItems(1);
        assertEquals(spanItems.size(), 1);
        SpanData spanData = spanItems.get(0);
        assertEquals(spanData.getResource().getAttribute(TestCustomizer.TEST_KEY), TestCustomizer.TEST_VALUE);

        // Check that the other customizers added were called
        // Note: propagator listed twice since by default there are two propagators (W3C trace and W3C baggage)
        assertTrue(TestCustomizer.LOGGED_EVENTS.contains("propagator"));
        assertTrue(TestCustomizer.LOGGED_EVENTS.contains("properties"));
        assertTrue(TestCustomizer.LOGGED_EVENTS.contains("sampler"));
        assertTrue(TestCustomizer.LOGGED_EVENTS.contains("exporter"));
        assertTrue(TestCustomizer.LOGGED_EVENTS.contains("tracer"));
    }
}
