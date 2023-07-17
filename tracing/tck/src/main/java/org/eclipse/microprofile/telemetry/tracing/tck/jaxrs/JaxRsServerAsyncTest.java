/*
 * Copyright (c) 2016-2022 Contributors to the Eclipse Foundation
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

package org.eclipse.microprofile.telemetry.tracing.tck.jaxrs;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.function.Function;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset;
import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

class JaxRsServerAsyncTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.bsp.schedule.delay", "100")
                .add("otel.sdk.disabled", "false")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, HttpServletRequest.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    private static final String TEST_VALUE = "test.value";

    @Inject
    private Tracer tracer;

    @Inject
    private InMemorySpanExporter spanExporter;

    @Inject
    private HttpServletRequest request;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
        }
    }

    @Test
    public void testJaxRsServerAsyncCompletionStage() {
        doAsyncTest(JaxRsServerAsyncTestEndpointClient::getCompletionStage);
    }

    @Test
    public void testJaxRsServerAsyncSuspend() {
        doAsyncTest(JaxRsServerAsyncTestEndpointClient::getSuspend);
    }

    private void doAsyncTest(Function<JaxRsServerAsyncTestEndpointClient, String> requestFunction) {
        Baggage baggage = Baggage.builder()
                .put(JaxRsServerAsyncTestEndpoint.BAGGAGE_KEY, TEST_VALUE)
                .build();

        try (Scope s = baggage.makeCurrent()) {
            // Make the request to the test endpoint
            JaxRsServerAsyncTestEndpointClient client = RestClientBuilder.newBuilder()
                    .baseUri(JaxRsServerAsyncTestEndpoint.getBaseUri(request))
                    .build(JaxRsServerAsyncTestEndpointClient.class);
            String response = requestFunction.apply(client);
            Assert.assertEquals("OK", response);
        } ;

        List<SpanData> spanData = spanExporter.getFinishedSpanItems(4);// , span.getSpanContext().getTraceId());

        // Assert correct parent-child links
        // Shows that propagation occurred
        // TestSpans.assertLinearParentage(spanData);

        SpanData testSpan = spanExporter.getFirst(SpanKind.INTERNAL);
        SpanData clientSpan = spanExporter.getFirst(SpanKind.CLIENT);
        SpanData serverSpan = spanExporter.getFirst(SpanKind.SERVER);
        // SpanData subtaskSpan = spanData.get(3);

        // Assert baggage propagated on server span
        /*
         * Assert.assertTrue(serverSpan.isSpan() .withKind(SpanKind.SERVER) .withAttribute(BAGGAGE_VALUE_ATTR,
         * TEST_VALUE));
         */
        // Assert baggage propagated on subtask span
        /*
         * Assert.assertTrue(subtaskSpan.isSpan() .withKind(SpanKind.INTERNAL) .withAttribute(BAGGAGE_VALUE_ATTR,
         * TEST_VALUE));
         */

        // Assert that the server span finished after the subtask span
        // Even though the resource method returned quickly, the span should not end until the response is actually
        // returned
        // Assert.assertTrue(serverSpan.getEndEpochNanos().greaterThan(subtaskSpan.getEndEpochNanos()));
    }
}
