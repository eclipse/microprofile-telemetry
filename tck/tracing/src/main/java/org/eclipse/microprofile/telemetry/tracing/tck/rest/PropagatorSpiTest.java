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

import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.microprofile.telemetry.tracing.tck.rest.PropagationHelper.SpanResourceClient;

import java.net.URL;

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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

public class PropagatorSpiTest extends Arquillian {
    public static final String TEST_VALUE = "test-value";

    public static final String TEST_KEY = "test-key";

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(TestPropagator.class, TestPropagatorProvider.class, InMemorySpanExporter.class,
                        InMemorySpanExporterProvider.class, PropagationHelper.class,
                        SpanResourceClient.class)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsServiceProvider(ConfigurablePropagatorProvider.class, TestPropagatorProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsResource(
                        new StringAsset("otel.sdk.disabled=false\notel.propagators=" + TestPropagatorProvider.NAME
                                + "\notel.traces.exporter=in-memory\notel.metrics.exporter=none"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");

    }

    @ArquillianResource
    private URL url;

    @Inject
    private InMemorySpanExporter exporter;

    @Inject
    private Baggage baggage;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (exporter != null) {
            exporter.reset();
        }
    }

    @Test
    void testSPIPropagator() {
        try (Scope s = baggage.toBuilder().put(TEST_KEY, TEST_VALUE).build().makeCurrent()) {
            WebTarget target = ClientBuilder.newClient().target(url.toString()).path("baggage");
            Response response = target.request().get();
            Assert.assertEquals(response.getStatus(), HTTP_OK);
        }

        exporter.assertSpanCount(2);

        SpanData server = exporter.getFirst(SpanKind.SERVER);
        Assert.assertEquals(TEST_VALUE, server.getAttributes().get(AttributeKey.stringKey(TEST_KEY)));

        SpanData client = exporter.getFirst(SpanKind.CLIENT);
        // Check that trace context propagation worked by checking that the parent was set correctly
        Assert.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Path("/baggage")
    public static class BaggageResource {
        @Inject
        private Baggage baggage;

        @Inject
        private Span span;

        @GET
        public Response get(@Context HttpHeaders headers) {
            try {
                // Check the TestPropagator headers were used
                Assert.assertNotNull(headers.getHeaderString(TestPropagator.TRACE_KEY));
                Assert.assertNotNull(headers.getHeaderString(TestPropagator.BAGGAGE_KEY));

                // Test that the default W3C headers were not used
                Assert.assertNull(headers.getHeaderString("traceparent"));
                Assert.assertNull(headers.getHeaderString("tracestate"));
                Assert.assertNull(headers.getHeaderString("baggage"));

                // Copy TEST_KEY from baggage into a span attribute
                span.setAttribute(TEST_KEY, baggage.getEntryValue(TEST_KEY));
                return Response.ok().build();
            } catch (Throwable e) {
                // An error here won't get reported back fully, so output it to the log as well
                System.err.println("Baggage Resource Exception:");
                e.printStackTrace();
                throw e;
            }
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}
