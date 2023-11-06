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
package org.eclipse.microprofile.telemetry.tracing.tck.rest;

import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.BAGGAGE_KEY;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.BAGGAGE_METADATA;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.BAGGAGE_METADATA_ATTR;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.BAGGAGE_VALUE;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.BAGGAGE_VALUE_ATTR;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.PROPAGATION_HEADERS_ATTR;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.SpanResourceClient;

import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;

public class W3PropagationTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, PropagationHelper.class,
                        SpanResourceClient.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(
                        new StringAsset(
                                "otel.sdk.disabled=false\notel.traces.exporter=in-memory\notel.propagators=tracecontext"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @ArquillianResource
    private URL url;
    @Inject
    private InMemorySpanExporter spanExporter;

    private SpanResourceClient client;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();

            try {
                // Create client manually so we can pass in URL from arquillian
                client = RestClientBuilder.newBuilder().baseUri(url.toURI()).build(SpanResourceClient.class);
            } catch (IllegalStateException | RestClientDefinitionException | URISyntaxException e) {
                Assert.fail("Failed to create rest client", e);
            }
        }
    }

    @Test
    void span() {

        Baggage baggage = Baggage.builder()
                .put(BAGGAGE_KEY, BAGGAGE_VALUE, BaggageEntryMetadata.create(BAGGAGE_METADATA)).build();
        try (Scope s = baggage.makeCurrent()) {
            client.span();
        }
        // assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(2);

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);

        // Baggage is not propagated if only tracecontext is enabled
        Assert.assertNull(server.getAttributes().get(BAGGAGE_VALUE_ATTR));
        Assert.assertNull(server.getAttributes().get(BAGGAGE_METADATA_ATTR));

        // Assert that the expected headers were used
        Assert.assertTrue(server.getAttributes().get(PROPAGATION_HEADERS_ATTR).contains("traceparent"));
    }
}
