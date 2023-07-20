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

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_HOST_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_HOST_PORT;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_PEER_PORT;
import static java.net.HttpURLConnection.HTTP_OK;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.testng.Assert;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@ApplicationPath("/")
@Path("MpRestClientAsyncTestEndpoint")
public class MpRestClientAsyncTestEndpoint extends Application {

    public static final String TEST_PASSED = "Test Passed";

    @Inject
    private InMemorySpanExporter spanExporter;

    @Inject
    private HttpServletRequest request;

    private Client client;

    @PostConstruct
    private void openClient() {
        client = ClientBuilder.newClient();
    }

    @PreDestroy
    private void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @GET
    @Path("/mpclient")
    public Response getMPRestClient(@Context UriInfo uriInfo) {
        Assert.assertNotNull(Span.current());

        try (Scope s = Baggage.builder().put("foo", "bar").build().makeCurrent()) {
            Baggage baggage = Baggage.current();
            Assert.assertEquals("bar", baggage.getEntryValue("foo"));

            String baseUrl = uriInfo.getAbsolutePath().toString().replace("/mpclient", "");
            URI baseUri = null;
            try {
                baseUri = new URI(baseUrl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Assert.assertNotNull(Span.current());
            MpClientTwo mpClientTwo = RestClientBuilder.newBuilder()
                    .baseUri(baseUri)
                    .build(MpClientTwo.class);

            String result = mpClientTwo.getMpClient();
            Assert.assertEquals(TEST_PASSED, result);
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    @GET
    @Path("/mpclientasync")
    public Response getMPRestClientAsync(@Context UriInfo uriInfo) {
        Assert.assertNotNull(Span.current());

        try (Scope s = Baggage.builder().put("foo", "bar").build().makeCurrent()) {
            Baggage baggage = Baggage.current();
            Assert.assertEquals("bar", baggage.getEntryValue("foo"));

            String baseUrl = uriInfo.getAbsolutePath().toString().replace("/mpclientasync", "");
            URI baseUri = null;
            try {
                baseUri = new URI(baseUrl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Assert.assertNotNull(Span.current());
            MpClientTwoAsync mpClientTwo = RestClientBuilder.newBuilder()
                    .baseUri(baseUri)
                    .build(MpClientTwoAsync.class);

            String result = mpClientTwo.getMpClient().toCompletableFuture().join();
            Assert.assertEquals(TEST_PASSED, result);
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    @GET
    @Path("/readspans/{traceId}")
    public Response readSpans(@Context UriInfo uriInfo, @PathParam("traceId") String traceId) {

        System.out.println("Reading spans");
        List<SpanData> spanData = spanExporter.getFinishedSpanItems(3);

        SpanData firstURL = spanExporter.getFirst(SpanKind.SERVER);
        SpanData httpGet = spanExporter.getFirst(SpanKind.CLIENT);
        SpanData secondURL = spanExporter.getNext(SpanKind.SERVER);

        // Assert correct parent-child links
        // Shows that propagation occurred
        Assert.assertEquals(httpGet.getSpanId(), firstURL.getParentSpanId());
        Assert.assertEquals(secondURL.getSpanId(), httpGet.getParentSpanId());

        Assert.assertEquals(firstURL.getTraceId(), traceId);
        Assert.assertEquals(httpGet.getTraceId(), traceId);
        Assert.assertEquals(secondURL.getTraceId(), traceId);

        Assert.assertEquals(HTTP_OK, firstURL.getAttributes().get(HTTP_STATUS_CODE).intValue());
        Assert.assertEquals(HttpMethod.GET, firstURL.getAttributes().get(HTTP_METHOD));
        Assert.assertEquals("http", firstURL.getAttributes().get(HTTP_SCHEME));

        // There are many different URLs that will end up here. But all should contain "MpRestClientAsyncTestEndpoint"
        Assert.assertTrue(httpGet.getAttributes().get(HTTP_URL).contains("MpRestClientAsyncTestEndpoint"));

        // The request used to call /readspans should have the same hostname and port as the test request
        URI requestUri = uriInfo.getRequestUri();
        Assert.assertEquals(requestUri.getHost(), firstURL.getAttributes().get(NET_HOST_NAME));
        Assert.assertEquals(Long.valueOf(requestUri.getPort()), firstURL.getAttributes().get(NET_HOST_PORT));

        Assert.assertEquals("HTTP GET", httpGet.getName());
        Assert.assertEquals(HTTP_OK, httpGet.getAttributes().get(HTTP_STATUS_CODE).intValue());
        Assert.assertEquals(HttpMethod.GET, httpGet.getAttributes().get(HTTP_METHOD));
        Assert.assertEquals(requestUri.getHost(), httpGet.getAttributes().get(NET_PEER_NAME));
        Assert.assertEquals(Long.valueOf(requestUri.getPort()), httpGet.getAttributes().get(NET_PEER_PORT));
        Assert.assertTrue(httpGet.getAttributes().get(HTTP_URL).contains("MpRestClientAsyncTestEndpoint"));

        return Response.ok(TEST_PASSED).build();
    }

    @GET
    @Path("getMpClient")
    public Response getMpClient() {
        Assert.assertNotNull(Span.current());
        Baggage baggage = Baggage.current();

        Assert.assertEquals("bar", baggage.getEntryValue("foo"));

        return Response.ok(TEST_PASSED).build();
    }

    @RegisterRestClient
    public interface MpClientTwo {

        @GET
        @Path("getMpClient")
        public String getMpClient();

    }

    @RegisterRestClient
    public interface MpClientTwoAsync {

        @GET
        @Path("getMpClient")
        public CompletionStage<String> getMpClient();

    }
}
