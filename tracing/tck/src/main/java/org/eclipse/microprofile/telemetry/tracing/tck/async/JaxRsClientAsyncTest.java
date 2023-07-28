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

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_SCHEME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_TARGET;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static java.net.HttpURLConnection.HTTP_OK;

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
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;

public class JaxRsClientAsyncTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.bsp.schedule.delay", "100")
                .add("otel.sdk.disabled", "false")
                .add("otel.traces.exporter", "in-memory");

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemorySpanExporter.class, InMemorySpanExporterProvider.class, BasicHttpClient.class,
                        JaxRsClientAsyncTestEndpoint.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableSpanExporterProvider.class, InMemorySpanExporterProvider.class)
                .addAsResource(config, "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    private BasicHttpClient basicClient;

    @ArquillianResource
    URL url;

    public static final String TEST_PASSED = "Test Passed";

    @Inject
    private InMemorySpanExporter spanExporter;

    @BeforeMethod
    void setUp() {
        // Only want to run on server
        if (spanExporter != null) {
            spanExporter.reset();
            basicClient = new BasicHttpClient(url);
        }
    }

    @Test
    public void testIntegrationWithJaxRsClient() throws Exception {
        basicClient.get("/JaxRsClientAsyncTestEndpoint/jaxrsclient");
        readSpans();
    }

    @Test
    public void testIntegrationWithJaxRsClientAsync() throws Exception {
        basicClient.get("/JaxRsClientAsyncTestEndpoint/jaxrsclientasync");
        readSpans();
    }

    public void readSpans() {

        List<SpanData> spanData = spanExporter.getFinishedSpanItems(3);

        List<SpanData> serverSpans = spanExporter.getSpansWithKind(SpanKind.SERVER);

        SpanData firstURL = null;
        SpanData secondURL = null;
        for (SpanData span : serverSpans) {
            if (span.getAttributes().get(HTTP_TARGET).contains("JaxRsClientAsyncTestEndpoint/jaxrsclient")) {
                firstURL = span;
            } else {
                secondURL = span;
            }
        }

        Assert.assertNotNull(firstURL);
        Assert.assertNotNull(secondURL);

        SpanData httpGet = spanExporter.getFirst(SpanKind.CLIENT);

        // Assert correct parent-child links
        // Shows that propagation occurred
        Assert.assertEquals(httpGet.getSpanId(), secondURL.getParentSpanId());
        Assert.assertEquals(firstURL.getSpanId(), httpGet.getParentSpanId());

        Assert.assertEquals(HTTP_OK, firstURL.getAttributes().get(HTTP_STATUS_CODE).intValue());
        Assert.assertEquals(HttpMethod.GET, firstURL.getAttributes().get(HTTP_METHOD));
        Assert.assertEquals("http", firstURL.getAttributes().get(HTTP_SCHEME));

        // There are many different URLs that will end up here. But all should contain "JaxRsClientAsyncTestEndpoint"
        Assert.assertTrue(httpGet.getAttributes().get(HTTP_URL).contains("JaxRsClientAsyncTestEndpoint"));

        Assert.assertEquals(HTTP_OK, httpGet.getAttributes().get(HTTP_STATUS_CODE).intValue());
        Assert.assertEquals(HttpMethod.GET, httpGet.getAttributes().get(HTTP_METHOD));
        Assert.assertTrue(httpGet.getAttributes().get(HTTP_URL).contains("JaxRsClientAsyncTestEndpoint"));
    }
}
