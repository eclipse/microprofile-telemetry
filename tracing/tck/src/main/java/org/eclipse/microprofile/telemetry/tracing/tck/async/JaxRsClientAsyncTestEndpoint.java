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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.testng.Assert;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("JaxRsClientAsyncTestEndpoint")
public class JaxRsClientAsyncTestEndpoint {

    public static final String TEST_PASSED = "Test Passed";

    @Inject
    private InMemorySpanExporter spanExporter;

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
    @Path("/jaxrsclient")
    public Response getJax(@Context UriInfo uriInfo, @QueryParam(value = "baggageValue") String baggageValue) {
        Assert.assertNotNull(Span.current());

        try (Scope s = Baggage.builder().put("foo", baggageValue).build().makeCurrent()) {
            Baggage baggage = Baggage.current();
            Assert.assertEquals(baggage.getEntryValue("foo"), baggageValue);

            String url = new String(uriInfo.getAbsolutePath().toString());
            // Use our own URL to work out the URL of the other test endpoint
            url = url.replace("jaxrsclient", "jaxrstwo");

            String result = client.target(url)
                    .queryParam("baggageValue", baggageValue)
                    .request(MediaType.TEXT_PLAIN)
                    .get(String.class);
            Assert.assertEquals(result, TEST_PASSED);
        } finally {
            client.close();
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    @GET
    @Path("/jaxrsclientasync")
    public Response getJaxAsync(@Context UriInfo uriInfo, @QueryParam(value = "baggageValue") String baggageValue) {
        Assert.assertNotNull(Span.current());

        try (Scope s = Baggage.builder().put("foo", baggageValue).build().makeCurrent()) {
            Baggage baggage = Baggage.current();
            Assert.assertEquals(baggage.getEntryValue("foo"), baggageValue);

            String url = new String(uriInfo.getAbsolutePath().toString());
            // Use our own URL to work out the URL of the other test endpoint
            url = url.replace("jaxrsclientasync", "jaxrstwo");

            Client client = ClientBuilder.newClient();
            Future<String> result = client.target(url)
                    .queryParam("baggageValue", baggageValue)
                    .request(MediaType.TEXT_PLAIN)
                    .async()
                    .get(String.class);
            try {
                String resultValue = result.get(10, SECONDS);
                Assert.assertEquals(resultValue, TEST_PASSED);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                client.close();
            }
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    @GET
    @Path("/jaxrsclienterror")
    public Response getJaxError(@Context UriInfo uriInfo, @QueryParam(value = "baggageValue") String baggageValue) {
        Assert.assertNotNull(Span.current());

        try (Scope s = Baggage.builder().put("foo", baggageValue).build().makeCurrent()) {
            Baggage baggage = Baggage.current();
            Assert.assertEquals(baggage.getEntryValue("foo"), baggageValue);

            String url = new String(uriInfo.getAbsolutePath().toString());
            // Use our own URL to work out the URL of the other test endpoint
            url = url.replace("jaxrsclienterror", "error");

            Client client = ClientBuilder.newClient();
            Future<String> result = client.target(url)
                    .request(MediaType.TEXT_PLAIN)
                    .async()
                    .get(String.class);
            try {
                result.get(10, SECONDS);
                fail("Client didn't throw an exception");
            } catch (ExecutionException e) {
                // Expected because server returned BAD_REQUEST
                WebApplicationException webEx = (WebApplicationException) e.getCause();
                assertEquals(webEx.getResponse().getStatus(), HTTP_BAD_REQUEST);
            } catch (Exception e) {
                // Wrap and throw unexpected exceptions
                throw new RuntimeException(e);
            } finally {
                client.close();
            }
        }
        return Response.ok(Span.current().getSpanContext().getTraceId()).build();
    }

    // A method to be called by JAX Clients
    // This method triggers span creation and span propagation is automatic.
    @GET
    @Path("/jaxrstwo")
    public Response getJaxRsTwo(@QueryParam(value = "baggageValue") String baggageValue) {
        Assert.assertNotNull(Span.current());
        Baggage baggage = Baggage.current();
        // Assert that Baggage is propagated from Jax Server to Jax Client
        Assert.assertEquals(baggage.getEntryValue("foo"), baggageValue);
        return Response.ok(TEST_PASSED).build();
    }

    @GET
    @Path("/error")
    public Response getError() {
        return Response.status(HTTP_BAD_REQUEST).build();
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}
