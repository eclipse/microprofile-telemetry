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

import static java.net.HttpURLConnection.HTTP_OK;

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
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

public class BaggageTest extends Arquillian {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsResource(new StringAsset("otel.sdk.disabled=false\notel.traces.exporter=in-memory"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @ArquillianResource
    private URL url;

    @Inject
    private InMemorySpanExporter spanExporter;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
        }
    }

    @Test
    void baggage() {
        WebTarget target = ClientBuilder.newClient().target(url.toString()).path("baggage");
        Response response = target.request().header("baggage", "user=naruto").get();
        Assert.assertEquals(response.getStatus(), HTTP_OK);

        spanExporter.getFinishedSpanItems(2);
    }

    @Path("/baggage")
    public static class BaggageResource {
        @Inject
        private Baggage baggage;

        @GET
        public Response baggage() {
            try {
                Assert.assertEquals(baggage.getEntryValue("user"), "naruto");
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
