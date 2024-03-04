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

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;

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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

public class RestClientSpanDefaultTest extends Arquillian {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(new StringAsset("otel.traces.exporter=in-memory\notel.metrics.exporter=none"),
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
        Assert.assertEquals(response.getStatus(), HTTP_OK);
        spanExporter.getFinishedSpanItems(0);
    }

    @Test
    void spanName() {
        Response response = client.spanName("1");
        Assert.assertEquals(response.getStatus(), HTTP_OK);
        spanExporter.getFinishedSpanItems(0);
    }

    @Test
    void spanNameQuery() {
        Response response = client.spanNameQuery("1", "query");
        Assert.assertEquals(response.getStatus(), HTTP_OK);
        spanExporter.getFinishedSpanItems(0);
    }

    @Test
    void spanError() {
        // Can't use REST Client here due to org.jboss.resteasy.microprofile.client.DefaultResponseExceptionMapper
        WebTarget target = ClientBuilder.newClient().target(url.toString() + "span/error");
        Response response = target.request().get();
        Assert.assertEquals(response.getStatus(), HTTP_INTERNAL_ERROR);
        spanExporter.getFinishedSpanItems(0);
    }

    @Test
    void spanChild() {
        Response response = client.spanChild();
        Assert.assertEquals(response.getStatus(), HTTP_OK);
        spanExporter.getFinishedSpanItems(0);
    }

    @Test
    void spanCurrent() {
        Response response = client.spanCurrent();
        Assert.assertEquals(response.getStatus(), HTTP_OK);
        spanExporter.getFinishedSpanItems(0);
    }

    @Test
    void spanNew() {
        Response response = client.spanNew();
        Assert.assertEquals(response.getStatus(), HTTP_OK);
        spanExporter.getFinishedSpanItems(0);
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
        @Path("/span/current")
        public Response spanCurrent() {
            span.setAttribute("tck.current.key", "tck.current.value");
            return Response.ok().build();
        }

        @GET
        @Path("/span/new")
        public Response spanNew() {
            Span span = tracer.spanBuilder("span.new")
                    .setSpanKind(INTERNAL)
                    .setParent(Context.current().with(this.span))
                    .setAttribute("tck.new.key", "tck.new.value")
                    .startSpan();

            span.end();

            return Response.ok().build();
        }
    }

    @ApplicationScoped
    public static class SpanBean {
        @WithSpan
        void spanChild() {

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
