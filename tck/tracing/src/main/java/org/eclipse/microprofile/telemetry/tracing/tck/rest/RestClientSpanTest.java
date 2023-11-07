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

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_TARGET;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_HOST_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_HOST_PORT;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_PORT;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

public class RestClientSpanTest extends Arquillian {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(new StringAsset("otel.sdk.disabled=false\notel.traces.exporter=in-memory"),
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
        Response response = client.span();
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(2);

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span");

        assertEquals(client.getTraceId(), server.getTraceId());
        assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanName() {
        Response response = client.spanName("1");
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(2);

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/1");
        assertFalse(server.getName().contains("span/1"),
                "Span name should not contain full path when using @PathParam");
        assertTrue(server.getAttributes().get(HTTP_ROUTE).contains("span/{name}"),
                "Route should contain path template");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/1");
        assertFalse(client.getName().contains("span/1"),
                "Span name should not contain full path when using @PathParam");

        assertEquals(server.getTraceId(), client.getTraceId());
        assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanNameQuery() {
        Response response = client.spanNameQuery("1", "query");
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(2);

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/1?query=query");
        assertFalse(server.getName().contains("=query"),
                "Span name should not contain query when using @QueryParam");
        assertFalse(server.getAttributes().get(HTTP_ROUTE).contains("=query"),
                "Route should not contain query when using @QueryParam");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/1?query=query");
        assertFalse(client.getName().contains("=query"),
                "Span name should not contain query when using @QueryParam");

        assertEquals(client.getTraceId(), server.getTraceId());
        assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanError() {
        // Can't use REST Client here due to org.jboss.resteasy.microprofile.client.DefaultResponseExceptionMapper
        WebTarget target = ClientBuilder.newClient().target(url.toString() + "span/error");
        Response response = target.request().get();
        assertResponseStatus(response, INTERNAL_SERVER_ERROR);

        spanExporter.assertSpanCount(2);

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/error", INTERNAL_SERVER_ERROR);
        // "For HTTP status codes in the 5xx range, as well as any other code the client failed to interpret, span
        // status MUST be set to Error."
        assertEquals(server.getStatus().getStatusCode(), StatusCode.ERROR);

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/error", INTERNAL_SERVER_ERROR);
        // "For HTTP status codes in the 5xx range, as well as any other code the client failed to interpret, span
        // status MUST be set to Error."
        assertEquals(client.getStatus().getStatusCode(), StatusCode.ERROR);

        assertEquals(client.getTraceId(), server.getTraceId());
        assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanChildWithParameter() {
        Response response = client.spanChildWithParameter("testParameterValue");
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(3);

        SpanData internal = spanExporter.getFirst(SpanKind.INTERNAL);
        assertEquals(internal.getKind(), SpanKind.INTERNAL);
        assertEquals(internal.getName(), "SpanBean.spanChildWithParameter");
        assertEquals(internal.getAttributes().get(AttributeKey.stringKey("testParameter")), "testParameterValue");

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/childParameterWithParameter/testParameterValue");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/childParameterWithParameter/testParameterValue");

        assertEquals(internal.getTraceId(), client.getTraceId());
        assertEquals(server.getTraceId(), client.getTraceId());
        assertEquals(server.getSpanId(), internal.getParentSpanId());
        assertEquals(client.getSpanId(), server.getParentSpanId());
    }

    @Test
    void spanChild() {
        Response response = client.spanChild();
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(3);

        SpanData internal = spanExporter.getFirst(SpanKind.INTERNAL);
        assertEquals(internal.getKind(), SpanKind.INTERNAL);
        assertEquals(internal.getName(), "SpanBean.spanChild");

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/child");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/child");

        assertEquals(internal.getTraceId(), client.getTraceId());
        assertEquals(server.getTraceId(), client.getTraceId());
        assertEquals(server.getSpanId(), internal.getParentSpanId());
        assertEquals(client.getSpanId(), server.getParentSpanId());
    }

    @Test
    void spanCurrent() {
        Response response = client.spanCurrent();
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(2);

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/current");
        assertEquals(server.getAttributes().get(AttributeKey.stringKey("tck.current.key")), "tck.current.value");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/current");

        assertEquals(server.getTraceId(), client.getTraceId());
        assertEquals(client.getSpanId(), server.getParentSpanId());
    }

    @Test
    void spanNew() {
        Response response = client.spanNew();
        assertResponseStatus(response, OK);

        spanExporter.assertSpanCount(3);

        SpanData internal = spanExporter.getFirst(SpanKind.INTERNAL);
        assertEquals(internal.getKind(), SpanKind.INTERNAL);
        assertEquals(internal.getName(), "span.new");
        assertEquals(internal.getAttributes().get(AttributeKey.stringKey("tck.new.key")), "tck.new.value");

        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/new");

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/new");

        assertEquals(internal.getTraceId(), client.getTraceId());
        assertEquals(server.getTraceId(), client.getTraceId());
        assertEquals(server.getSpanId(), internal.getParentSpanId());
        assertEquals(client.getSpanId(), server.getParentSpanId());
    }

    @Test
    void spanClientError() {
        // Can't use REST Client here due to org.jboss.resteasy.microprofile.client.DefaultResponseExceptionMapper
        WebTarget target = ClientBuilder.newClient().target(url.toString() + "span/clienterror");
        Response response = target.request().get();
        assertResponseStatus(response, BAD_REQUEST);

        spanExporter.assertSpanCount(2);
        // "For HTTP status codes in the 4xx range span status MUST be left unset in case of SpanKind.SERVER and MUST be
        // set to Error in case of
        // SpanKind.CLIENT"
        SpanData server = spanExporter.getFirst(SpanKind.SERVER);
        assertServerSpan(server, "span/clienterror", BAD_REQUEST);
        assertEquals(server.getStatus().getStatusCode(), StatusCode.UNSET);

        SpanData client = spanExporter.getFirst(SpanKind.CLIENT);
        assertClientSpan(client, "span/clienterror", BAD_REQUEST);
        assertEquals(client.getStatus().getStatusCode(), StatusCode.ERROR);

        assertEquals(client.getTraceId(), server.getTraceId());
        assertEquals(server.getParentSpanId(), client.getSpanId());
    }
    private void assertClientSpan(SpanData client, String path) {
        assertClientSpan(client, path, OK);
    }

    private void assertClientSpan(SpanData client, String path, Response.StatusType status) {
        assertEquals(client.getKind(), SpanKind.CLIENT);
        assertEquals(client.getAttributes().get(HTTP_STATUS_CODE).intValue(), status.getStatusCode());
        assertEquals(client.getAttributes().get(HTTP_METHOD), HttpMethod.GET);
        assertEquals(client.getAttributes().get(HTTP_URL), url.toString() + path);
        assertEquals(client.getAttributes().get(NET_PEER_NAME), url.getHost());
        if (url.getPort() != url.getDefaultPort()) {
            assertEquals(client.getAttributes().get(NET_PEER_PORT).intValue(), url.getPort());
        }
    }

    private void assertServerSpan(SpanData server, String path) {
        assertServerSpan(server, path, OK);
    }

    private void assertServerSpan(SpanData server, String path, Response.StatusType status) {
        assertEquals(server.getKind(), SpanKind.SERVER);
        assertEquals(server.getAttributes().get(HTTP_STATUS_CODE).intValue(), status.getStatusCode());
        assertEquals(server.getAttributes().get(HTTP_METHOD), HttpMethod.GET);
        assertEquals(server.getAttributes().get(HTTP_SCHEME), url.getProtocol());
        assertEquals(server.getAttributes().get(HTTP_TARGET), url.getPath() + path);
        // route is required when available, definitely available for REST endpoints
        Assert.assertNotNull(server.getAttributes().get(HTTP_ROUTE));
        // not asserting specific value as it is only recommended, and should contain application prefix
        assertEquals(server.getAttributes().get(NET_HOST_NAME), url.getHost());
        if (url.getPort() != url.getDefaultPort()) {
            assertEquals(server.getAttributes().get(NET_HOST_PORT).intValue(), url.getPort());
        }
    }

    private void assertResponseStatus(Response response, Response.StatusType status) {
        assertEquals(response.getStatus(), status.getStatusCode());
    }

    @RequestScoped
    @Path("/")
    public static class SpanResource {
        @Inject
        private SpanBean spanBean;
        @Inject
        private Span span;
        @Inject
        private Tracer tracer;

        @GET
        @Path("/span")
        public Response span() {
            return Response.ok().build();
        }

        @GET
        @Path("/span/{name}")
        public Response spanName(@PathParam(value = "name") String name, @QueryParam("query") String query) {
            return Response.ok().build();
        }

        @GET
        @Path("/span/error")
        public Response spanError() {
            return Response.serverError().build();
        }

        @GET
        @Path("/span/child")
        public Response spanChild() {
            spanBean.spanChild();
            return Response.ok().build();
        }

        @GET
        @Path("/span/childParameterWithParameter/{name}")
        public Response spanChildWithParameter(@PathParam(value = "name") String name) {
            spanBean.spanChildWithParameter(name);
            return Response.ok().build();
        }

        @GET
        @Path("/span/current")
        public Response spanCurrent() {
            span.setAttribute("tck.current.key", "tck.current.value");
            return Response.ok().build();
        }

        @GET
        @Path("/span/new")
        public Response spanNew() {
            Span span = tracer.spanBuilder("span.new")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setParent(Context.current().with(this.span))
                    .setAttribute("tck.new.key", "tck.new.value")
                    .startSpan();

            span.end();

            return Response.ok().build();
        }

        @GET
        @Path("span/clienterror")
        public Response spanClientError() {
            return Response.status(BAD_REQUEST).build();
        }
    }

    @ApplicationScoped
    public static class SpanBean {
        @WithSpan
        void spanChild() {

        }

        @WithSpan
        void spanChildWithParameter(@SpanAttribute("testParameter") String testParameter) {

        }
    }

    @RegisterRestClient(configKey = "client")
    @Path("/")
    public interface SpanResourceClient {
        @GET
        @Path("/span")
        Response span();

        @GET
        @Path("/span/{name}")
        Response spanName(@PathParam(value = "name") String name);

        @GET
        @Path("/span/{name}")
        Response spanNameQuery(@PathParam(value = "name") String name, @QueryParam("query") String query);

        @GET
        @Path("/span/child")
        Response spanChild();

        @GET
        @Path("/span/childParameterWithParameter/{name}")
        Response spanChildWithParameter(@PathParam(value = "name") String name);

        @GET
        @Path("/span/current")
        Response spanCurrent();

        @GET
        @Path("/span/new")
        Response spanNew();
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}
