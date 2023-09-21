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
package org.eclipse.microprofile.telemetry.tracing.tck.async;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.testng.Assert;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("MpRestClientAsyncTestEndpoint")
public class MpRestClientAsyncTestEndpoint {

    public static final String TEST_PASSED = "Test Passed";
    public static final String TEST_VALUE = "TEST_VALUE";

    @Inject
    private InMemorySpanExporter spanExporter;

    @GET
    @Path("/mpclient")
    public Response requestMpClient(@Context UriInfo uriInfo) {
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

            String result = mpClientTwo.requestMpClient(TEST_VALUE);
            Assert.assertEquals(TEST_PASSED, result);
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    @GET
    @Path("/mpclientasync")
    public Response requestMpClientAsync(@Context UriInfo uriInfo) {
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

            String result = mpClientTwo.requestMpClient(TEST_VALUE).toCompletableFuture().join();
            Assert.assertEquals(TEST_PASSED, result);
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    @GET
    @Path("/mpclientasyncerror")
    public Response requestMpClientAsyncError(@Context UriInfo uriInfo) {
        Assert.assertNotNull(Span.current());

        try (Scope s = Baggage.builder().put("foo", "bar").build().makeCurrent()) {
            Baggage baggage = Baggage.current();
            Assert.assertEquals("bar", baggage.getEntryValue("foo"));

            String baseUrl = uriInfo.getAbsolutePath().toString().replace("/mpclientasyncerror", "");
            URI baseUri = null;
            try {
                baseUri = new URI(baseUrl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Assert.assertNotNull(Span.current());
            MpClientTwoAsyncError mpClientTwoError = RestClientBuilder.newBuilder()
                    .baseUri(baseUri)
                    .build(MpClientTwoAsyncError.class);

            String result = mpClientTwoError.requestMpClientError(TEST_VALUE)
                    .exceptionally(e -> "Exception:" + extractResponseStatus(e))
                    .toCompletableFuture()
                    .join();

            assertEquals("Exception:400", result);
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    /**
     * Search the cause chain of an exception to find a WebApplicationException and extract the status code
     *
     * @param originalException
     *            the exception
     * @return the status code
     */
    private static int extractResponseStatus(Throwable originalException) {
        Throwable t = originalException;
        // Look up the cause chain to find a WebApplicationException and extract the response
        while (t != null && !(t instanceof WebApplicationException)) {
            t = t.getCause();
        }
        if (t == null) {
            throw new RuntimeException("Non WebApplicationException thrown", originalException);
        }
        WebApplicationException webEx = (WebApplicationException) t;
        return webEx.getResponse().getStatus();
    }

    @GET
    @Path("requestMpClient")
    public Response requestMpClient(@QueryParam("value") String value) {
        Assert.assertNotNull(Span.current());
        Assert.assertEquals(TEST_VALUE, value);
        Baggage baggage = Baggage.current();

        Assert.assertEquals("bar", baggage.getEntryValue("foo"));

        return Response.ok(TEST_PASSED).build();
    }

    @GET
    @Path("requestMpClientError")
    public Response requestMpClientError(@QueryParam("value") String value) {
        Assert.assertNotNull(Span.current());
        Assert.assertEquals(TEST_VALUE, value);
        Baggage baggage = Baggage.current();

        Assert.assertEquals("bar", baggage.getEntryValue("foo"));

        return Response.status(HTTP_BAD_REQUEST).build();
    }

    public interface MpClientTwo {

        @GET
        @Path("requestMpClient")
        public String requestMpClient(@QueryParam("value") String value);

    }

    public interface MpClientTwoAsync {

        @GET
        @Path("requestMpClient")
        public CompletionStage<String> requestMpClient(@QueryParam("value") String value);

    }

    public interface MpClientTwoAsyncError {

        @GET
        @Path("requestMpClientError")
        public CompletionStage<String> requestMpClientError(@QueryParam("value") String value);

    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}
