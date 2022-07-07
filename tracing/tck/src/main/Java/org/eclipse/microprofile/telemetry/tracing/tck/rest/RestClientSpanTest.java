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

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_HOST;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SERVER_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_TARGET;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;

import java.net.URL;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.annotations.WithSpan;
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

@ExtendWith(ArquillianExtension.class)
class RestClientSpanTest {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsResource(new StringAsset("client/mp-rest/url=${baseUri}\n"
                        + "otel.experimental.sdk.enabled=true"),
                        "META-INF/microprofile-config.properties");
    }

    @ArquillianResource
    URL url;
    @Inject
    InMemorySpanExporter spanExporter;
    @Inject
    @RestClient
    SpanResourceClient client;

    @BeforeEach
    void setUp() {
        spanExporter.reset();
    }

    @Test
    void span() {
        Response response = client.span();
        Assertions.assertEquals(response.getStatus(), HTTP_OK);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(2);

        SpanData server = spans.get(0);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span", server.getName());
        Assertions.assertEquals(HTTP_OK, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span", server.getAttributes().get(HTTP_TARGET));

        SpanData client = spans.get(1);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_OK, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(client.getTraceId(), server.getTraceId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanName() {
        Response response = client.spanName("1");
        Assertions.assertEquals(response.getStatus(), HTTP_OK);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(2);

        SpanData server = spans.get(0);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span/{name}", server.getName());
        Assertions.assertEquals(HTTP_OK, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span/1", server.getAttributes().get(HTTP_TARGET));

        SpanData client = spans.get(1);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_OK, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span/1", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(server.getTraceId(), client.getTraceId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanNameQuery() {
        Response response = client.spanNameQuery("1", "query");
        Assertions.assertEquals(response.getStatus(), HTTP_OK);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(2);

        SpanData server = spans.get(0);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span/{name}", server.getName());
        Assertions.assertEquals(HTTP_OK, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span/1?query=query", server.getAttributes().get(HTTP_TARGET));

        SpanData client = spans.get(1);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_OK, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span/1?query=query", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(client.getTraceId(), server.getTraceId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanError() {
        // Can't use REST Client here due to org.jboss.resteasy.microprofile.client.DefaultResponseExceptionMapper
        WebTarget target = ClientBuilder.newClient().target(url.toString() + "span/error");
        Response response = target.request().get();
        Assertions.assertEquals(response.getStatus(), HTTP_INTERNAL_ERROR);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(2);

        SpanData server = spans.get(0);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span/error", server.getName());
        Assertions.assertEquals(HTTP_INTERNAL_ERROR, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span/error", server.getAttributes().get(HTTP_TARGET));

        SpanData client = spans.get(1);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_INTERNAL_ERROR, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span/error", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(client.getTraceId(), server.getTraceId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanChild() {
        Response response = client.spanChild();
        Assertions.assertEquals(response.getStatus(), HTTP_OK);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(3);

        SpanData internal = spans.get(0);
        Assertions.assertEquals(INTERNAL, internal.getKind());
        Assertions.assertEquals("SpanBean.spanChild", internal.getName());

        SpanData server = spans.get(1);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span/child", server.getName());
        Assertions.assertEquals(HTTP_OK, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span/child", server.getAttributes().get(HTTP_TARGET));

        SpanData client = spans.get(2);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_OK, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span/child", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(client.getTraceId(), internal.getTraceId());
        Assertions.assertEquals(client.getTraceId(), server.getTraceId());
        Assertions.assertEquals(internal.getParentSpanId(), server.getSpanId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanCurrent() {
        Response response = client.spanCurrent();
        Assertions.assertEquals(response.getStatus(), HTTP_OK);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(2);

        SpanData server = spans.get(0);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span/current", server.getName());
        Assertions.assertEquals(HTTP_OK, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span/current", server.getAttributes().get(HTTP_TARGET));
        Assertions.assertEquals("tck.current.value", server.getAttributes().get(stringKey("tck.current.key")));

        SpanData client = spans.get(1);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_OK, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span/current", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(client.getTraceId(), server.getTraceId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @Test
    void spanNew() {
        Response response = client.spanNew();
        Assertions.assertEquals(response.getStatus(), HTTP_OK);

        List<SpanData> spans = spanExporter.getFinishedSpanItems(3);

        SpanData internal = spans.get(0);
        Assertions.assertEquals(INTERNAL, internal.getKind());
        Assertions.assertEquals("span.new", internal.getName());
        Assertions.assertEquals("tck.new.value", internal.getAttributes().get(stringKey("tck.new.key")));

        SpanData server = spans.get(1);
        Assertions.assertEquals(SERVER, server.getKind());
        Assertions.assertEquals(url.getPath() + "span/new", server.getName());
        Assertions.assertEquals(HTTP_OK, server.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, server.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals("http", server.getAttributes().get(HTTP_SCHEME));
        Assertions.assertEquals(url.getHost(), server.getAttributes().get(HTTP_SERVER_NAME));
        Assertions.assertEquals(url.getHost() + ":" + url.getPort(), server.getAttributes().get(HTTP_HOST));
        Assertions.assertEquals(url.getPath() + "span/new", server.getAttributes().get(HTTP_TARGET));

        SpanData client = spans.get(2);
        Assertions.assertEquals(CLIENT, client.getKind());
        Assertions.assertEquals("HTTP GET", client.getName());
        Assertions.assertEquals(HTTP_OK, client.getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, client.getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.toString() + "span/new", client.getAttributes().get(HTTP_URL));

        Assertions.assertEquals(client.getTraceId(), internal.getTraceId());
        Assertions.assertEquals(client.getTraceId(), server.getTraceId());
        Assertions.assertEquals(internal.getParentSpanId(), server.getSpanId());
        Assertions.assertEquals(server.getParentSpanId(), client.getSpanId());
    }

    @RequestScoped
    @Path("/")
    public static class SpanResource {
        @Inject
        SpanBean spanBean;
        @Inject
        Span span;
        @Inject
        Tracer tracer;

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
