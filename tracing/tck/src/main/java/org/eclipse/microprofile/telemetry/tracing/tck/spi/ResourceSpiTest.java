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
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;

public class ResourceSpiTest extends Arquillian {

    public static final String TEST_VALUE1 = "test1";
    public static final String TEST_VALUE2 = "test2";

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, TestResourceProvider.class)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsServiceProvider(ResourceProvider.class, TestResourceProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsResource(new StringAsset("otel.sdk.disabled=false\notel.traces.exporter=in-memory\n"
                        + TestResourceProvider.TEST_KEY1.getKey() + "=" + TEST_VALUE1 + "\notel.test.key2="
                        + TEST_VALUE2),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

    }

    @Inject
    private Tracer tracer;

    @Inject
    private InMemorySpanExporter exporter;

    @Test
    public void testResource() {
        Span span = tracer.spanBuilder("span").startSpan();
        span.end();

        List<SpanData> spanItems = exporter.getFinishedSpanItems(1);
        assertEquals(spanItems.size(), 1);
        SpanData spanData = spanItems.get(0);
        assertEquals(spanData.getResource().getAttribute(TestResourceProvider.TEST_KEY1), TEST_VALUE1);
        assertEquals(spanData.getResource().getAttribute(TestResourceProvider.TEST_KEY2), TEST_VALUE2);
    }

}
