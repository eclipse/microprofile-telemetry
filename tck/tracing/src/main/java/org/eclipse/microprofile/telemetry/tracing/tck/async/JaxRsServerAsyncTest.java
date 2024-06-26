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

import static io.opentelemetry.semconv.SemanticAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.SemanticAttributes.URL_QUERY;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.eclipse.microprofile.telemetry.tracing.tck.async.JaxRsServerAsyncTestEndpoint.BAGGAGE_VALUE_ATTR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.function.Function;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset;
import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporterProvider;
import org.eclipse.microprofile.telemetry.tracing.tck.porting.PropertiesBasedConfigurationBuilder;
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
import jakarta.ws.rs.WebApplicationException;

public class JaxRsServerAsyncTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.bsp.schedule.delay", "100")
                .add("otel.sdk.disabled", "false")
                .add("otel.metrics.exporter", "none")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class,
                        JaxRsServerAsyncTestEndpointClient.class, JaxRsServerAsyncTestEndpoint.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addPackages(true, PropertiesBasedConfigurationBuilder.class.getPackage())
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    private static final String TEST_VALUE = "test.value";
    public static final String QUERY_VALUE = "bar";

    @Inject
    private InMemorySpanExporter spanExporter;

    @ArquillianResource
    private URL url;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
        }
    }

    @Test
    public void testJaxRsServerAsyncCompletionStage() {
        doAsyncTest((client) -> client.getCompletionStage(QUERY_VALUE));
    }

    @Test
    public void testJaxRsServerAsyncCompletionStageError() {
        doErrorAsyncTest((client) -> client.getCompletionStageError(QUERY_VALUE));
    }

    @Test
    public void testJaxRsServerAsyncSuspend() {
        doAsyncTest((client) -> client.getSuspend(QUERY_VALUE));
    }

    @Test
    public void testJaxRsServerAsyncSuspendError() {
        doErrorAsyncTest((client) -> client.getSuspendError(QUERY_VALUE));
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
                Assert.assertEquals(response, "OK");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        spanExporter.assertSpanCount(3);

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

        // Assert that query parameter was passed correctly
        Assert.assertTrue(serverSpan.getAttributes().get(URL_QUERY).contains(QUERY_VALUE));

        // Assert that the server span finished after the subtask span
        // Even though the resource method returned quickly, the span should not end until the response is actually
        // returned
        Assert.assertTrue(serverSpan.getEndEpochNanos() >= subtaskSpan.getEndEpochNanos());
    }

    private void doErrorAsyncTest(Function<JaxRsServerAsyncTestEndpointClient, String> requestFunction) {
        Baggage baggage = Baggage.builder()
                .put(JaxRsServerAsyncTestEndpoint.BAGGAGE_KEY, TEST_VALUE)
                .build();

        try (Scope s = baggage.makeCurrent()) {
            // Make the request to the test endpoint
            try {
                JaxRsServerAsyncTestEndpointClient client = RestClientBuilder.newBuilder()
                        .baseUri(url.toURI())
                        .build(JaxRsServerAsyncTestEndpointClient.class);
                try {
                    requestFunction.apply(client);
                    fail("Client did not throw an exception");
                } catch (WebApplicationException e) {
                    assertEquals(e.getResponse().getStatus(), HttpURLConnection.HTTP_BAD_REQUEST,
                            "expected " + HttpURLConnection.HTTP_BAD_REQUEST + " but got " + e.getResponse().getStatus()
                                    + " full output: " + e.getResponse().readEntity(String.class));
                    readErrorSpans();
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void readErrorSpans() {
        spanExporter.assertSpanCount(3);

        SpanData subtaskSpan = spanExporter.getFirst(SpanKind.INTERNAL);
        SpanData clientSpan = spanExporter.getFirst(SpanKind.CLIENT);
        SpanData serverSpan = spanExporter.getFirst(SpanKind.SERVER);

        // Assert correct parent-child links
        // Shows that propagation occurred
        assertEquals(serverSpan.getSpanId(), subtaskSpan.getParentSpanId());
        assertEquals(clientSpan.getSpanId(), serverSpan.getParentSpanId());

        // Assert the status code for the client and server spans
        assertEquals(serverSpan.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_BAD_REQUEST);
        assertEquals(clientSpan.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_BAD_REQUEST);

        // Assert that the expected headers were used
        Assert.assertTrue(serverSpan.getAttributes().get(BAGGAGE_VALUE_ATTR).contains(TEST_VALUE));

        // Assert baggage propagated on subtask span
        Assert.assertTrue(subtaskSpan.getAttributes().get(BAGGAGE_VALUE_ATTR).contains(TEST_VALUE));

        // Assert that query parameter was passed correctly
        Assert.assertTrue(serverSpan.getAttributes().get(URL_QUERY).contains(QUERY_VALUE));

        // Assert that the server span finished after the subtask span
        // Even though the resource method returned quickly, the span should not end until the response is actually
        // returned
        Assert.assertTrue(serverSpan.getEndEpochNanos() >= subtaskSpan.getEndEpochNanos());
    }
}
