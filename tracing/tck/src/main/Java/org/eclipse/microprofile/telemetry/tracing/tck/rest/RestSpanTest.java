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
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_TARGET;
import static io.restassured.RestAssured.given;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import io.restassured.RestAssured;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.telemetry.tracing.tck.exporter.InMemorySpanExporter;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.smallrye.opentelemetry.api.OpenTelemetryConfig;

@ExtendWith(ArquillianExtension.class)
class RestSpanTest {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class);
    }

    @ArquillianResource
    URL url;
    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void setUp() {
        spanExporter.reset();
    }

    @Test
    void span() {
        RestAssured.given().get("/span").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        Assertions.assertEquals(1, spanItems.size());
        Assertions.assertEquals(SERVER, spanItems.get(0).getKind());
        Assertions.assertEquals(url.getPath() + "span", spanItems.get(0).getName());
        Assertions.assertEquals(HTTP_OK, spanItems.get(0).getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, spanItems.get(0).getAttributes().get(HTTP_METHOD));

        Assertions.assertEquals("org/eclipse/microprofile/telemetry/tracing/tck", spanItems.get(0).getResource().getAttribute(SERVICE_NAME));
        Assertions.assertEquals("0.1.0-SNAPSHOT", spanItems.get(0).getResource().getAttribute(SERVICE_VERSION));

        InstrumentationLibraryInfo libraryInfo = spanItems.get(0).getInstrumentationLibraryInfo();
        //FIXME this will need to come from the MP Config bridge
        assertEquals(OpenTelemetryConfig.INSTRUMENTATION_NAME, libraryInfo.getName());
        assertEquals(OpenTelemetryConfig.INSTRUMENTATION_VERSION, libraryInfo.getVersion());
    }

    @Test
    void spanName() {
        RestAssured.given().get("/span/1").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        Assertions.assertEquals(1, spanItems.size());
        Assertions.assertEquals(SERVER, spanItems.get(0).getKind());
        Assertions.assertEquals(url.getPath() + "span/{name}", spanItems.get(0).getName());
        Assertions.assertEquals(HTTP_OK, spanItems.get(0).getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, spanItems.get(0).getAttributes().get(HTTP_METHOD));
    }

    @Test
    void spanNameWithoutQueryString() {
        RestAssured.given().get("/span/1?id=1").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        Assertions.assertEquals(1, spanItems.size());
        Assertions.assertEquals(SERVER, spanItems.get(0).getKind());
        Assertions.assertEquals(url.getPath() + "span/{name}", spanItems.get(0).getName());
        Assertions.assertEquals(HTTP_OK, spanItems.get(0).getAttributes().get(HTTP_STATUS_CODE));
        Assertions.assertEquals(HttpMethod.GET, spanItems.get(0).getAttributes().get(HTTP_METHOD));
        Assertions.assertEquals(url.getPath() + "span/1?id=1", spanItems.get(0).getAttributes().get(HTTP_TARGET));
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
