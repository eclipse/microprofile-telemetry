/*
 * Copyright (c) 2016-2023 Contributors to the Eclipse Foundation
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

package org.eclipse.microprofile.telemetry.tracing.tck.async;

import static org.eclipse.microprofile.telemetry.tracing.tck.async.JaxRsServerAsyncTestEndpoint.BAGGAGE_VALUE_ATTR;
import static org.testng.Assert.assertEquals;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Function;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset;
import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;

class JaxRsServerAsyncTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.bsp.schedule.delay", "100")
                .add("otel.sdk.disabled", "false")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class,
                        JaxRsServerAsyncTestEndpointClient.class, JaxRsServerAsyncTestEndpoint.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    private static final String TEST_VALUE = "test.value";

    @Inject
    private InMemorySpanExporter spanExporter;

    @ArquillianResource
    URL url;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
        }
    }

    @Test(groups = "optional-jaxrs-tests")
    public void testJaxRsServerAsyncCompletionStage() {
        doAsyncTest(JaxRsServerAsyncTestEndpointClient::getCompletionStage);
    }

    @Test(groups = "optional-jaxrs-tests")
    public void testJaxRsServerAsyncSuspend() {
        doAsyncTest(JaxRsServerAsyncTestEndpointClient::getSuspend);
    }

    private void doAsyncTest(Function<JaxRsServerAsyncTestEndpointClient, String> requestFunction) {
        Baggage baggage = Baggage.builder()
                .put(JaxRsServerAsyncTestEndpoint.BAGGAGE_KEY, TEST_VALUE)
                .build();

        try (Scope s = baggage.makeCurrent()) {
            // Make the request to the test endpoint
            try {
                JaxRsServerAsyncTestEndpointClient client = RestClientBuilder.newBuilder()
                        .baseUri(url.toURI())
                        .build(JaxRsServerAsyncTestEndpointClient.class);

                String response = requestFunction.apply(client);
                Assert.assertEquals("OK", response);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        // Assert correct parent-child links
        // Shows that propagation occurred
        SpanData subtaskSpan = spanExporter.getFirst(SpanKind.INTERNAL);
        SpanData clientSpan = spanExporter.getFirst(SpanKind.CLIENT);
        SpanData serverSpan = spanExporter.getFirst(SpanKind.SERVER);

        assertEquals(serverSpan.getSpanId(), subtaskSpan.getParentSpanId());
        assertEquals(clientSpan.getSpanId(), serverSpan.getParentSpanId());

        // Assert that the expected headers were used
        Assert.assertTrue(serverSpan.getAttributes().get(BAGGAGE_VALUE_ATTR).contains(TEST_VALUE));

        // Assert baggage propagated on subtask span
        Assert.assertTrue(subtaskSpan.getAttributes().get(BAGGAGE_VALUE_ATTR).contains(TEST_VALUE));

        // Assert that the server span finished after the subtask span
        // Even though the resource method returned quickly, the span should not end until the response is actually
        // returned
        Assert.assertTrue(serverSpan.getEndEpochNanos() >= subtaskSpan.getEndEpochNanos());
    }
}
