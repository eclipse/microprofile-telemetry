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

package org.eclipse.microprofile.telemetry.tracing.tck.rest;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_VERSION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_TARGET;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_HOST_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_HOST_PORT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.eclipse.microprofile.telemetry.tracing.tck.BasicHttpClient;
import org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset;
import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

class RestSpanTest extends Arquillian {

    private static final String TEST_SERVICE_NAME = "org/eclipse/microprofile/telemetry/tracing/tck";
    private static final String TEST_SERVICE_VERSION = "0.1.0-TEST";

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.service.name", TEST_SERVICE_NAME)
                .add("otel.resource.attributes", SERVICE_VERSION.getKey() + "=" + TEST_SERVICE_VERSION)
                .add("otel.sdk.disabled", "false")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, BasicHttpClient.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties");
    }

    @ArquillianResource
    URL url;
    @Inject
    InMemorySpanExporter spanExporter;

    private BasicHttpClient basicClient;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
            basicClient = new BasicHttpClient(url);
        }
    }

    private void assertServerSpan(SpanData server, String path) {
        assertServerSpan(server, path, HTTP_OK);
    }
    private void assertServerSpan(SpanData server, String path, int statusCode) {
        Assert.assertEquals(server.getKind(), SERVER);
        Assert.assertEquals(server.getAttributes().get(HTTP_STATUS_CODE).intValue(), statusCode);
        Assert.assertEquals(server.getAttributes().get(HTTP_METHOD), HttpMethod.GET);
        Assert.assertEquals(server.getAttributes().get(HTTP_SCHEME), url.getProtocol());
        Assert.assertEquals(server.getAttributes().get(HTTP_TARGET), url.getPath() + path);
        // route is required when available, definitely available for REST endpoints
        Assert.assertNotNull(server.getAttributes().get(HTTP_ROUTE));
        // not asserting specific value as it is only recommended, and should contain application prefix
        Assert.assertEquals(server.getAttributes().get(NET_HOST_NAME), url.getHost());
        if (url.getPort() != url.getDefaultPort()) {
            Assert.assertEquals(server.getAttributes().get(NET_HOST_PORT).intValue(), url.getPort());
        }
    }

    @Test
    void span() throws URISyntaxException, IOException {
        assertEquals(basicClient.get("/span"), HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(spanItems.size(), 1);
        SpanData span = spanItems.get(0);
        assertServerSpan(span, "span");

        assertEquals(
                span.getResource().getAttribute(SERVICE_NAME),
                TEST_SERVICE_NAME);
        assertEquals(span.getResource().getAttribute(SERVICE_VERSION), TEST_SERVICE_VERSION);

        InstrumentationScopeInfo libraryInfo = span.getInstrumentationScopeInfo();
        // Was decided at the MP Call on 13/06/2022 that lib name and version are responsibility of lib implementations
        assertNotNull(libraryInfo.getName());
    }

    @Test
    void spanName() {
        assertEquals(basicClient.get("/span/1"), HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(spanItems.size(), 1);
        SpanData span = spanItems.get(0);
        assertServerSpan(span, "span/1");
        Assert.assertFalse(span.getName().contains("span/1"),
                "Span name should not contain full path when using @PathParam");
        Assert.assertTrue(span.getAttributes().get(HTTP_ROUTE).contains("span/{name}"),
                "Route should contain path template");
    }

    @Test
    void spanNameWithoutQueryString() {
        assertEquals(basicClient.get("/span/1?id=1"), HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(spanItems.size(), 1);
        SpanData span = spanItems.get(0);
        assertServerSpan(span, "span/1?id=1");
        Assert.assertFalse(span.getName().contains("=1"), "Span name should not contain query when using @QueryParam");
        Assert.assertFalse(span.getAttributes().get(HTTP_ROUTE).contains("=1"),
                "Route should not contain query when using @QueryParam");
    }

    @Path("/")
    public static class SpanResource {
        @GET
        @Path("/span")
        public Response span() {
            return Response.ok().build();
        }

        @GET
        @Path("/span/{name}")
        public Response spanName(@PathParam(value = "name") String name) {
            return Response.ok().build();
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}
