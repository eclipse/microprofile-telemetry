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

import static io.opentelemetry.semconv.SemanticAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.SemanticAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.SemanticAttributes.URL_FULL;
import static io.opentelemetry.semconv.SemanticAttributes.URL_PATH;
import static io.opentelemetry.semconv.SemanticAttributes.URL_QUERY;
import static io.opentelemetry.semconv.SemanticAttributes.URL_SCHEME;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
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
import jakarta.ws.rs.HttpMethod;

public class JaxRsClientAsyncTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {

        ConfigAsset config = new ConfigAsset()
                .add("otel.bsp.schedule.delay", "100")
                .add("otel.sdk.disabled", "false")
                .add("otel.metrics.exporter", "none")
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
    private URL url;

    public static final String TEST_PASSED = "Test Passed";
    public static final String QUERY_VALUE = "bar";

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
        basicClient.get("/JaxRsClientAsyncTestEndpoint/jaxrsclient?baggageValue=" + QUERY_VALUE);
        readSpans();
    }

    @Test
    public void testIntegrationWithJaxRsClientAsync() throws Exception {
        basicClient.get("/JaxRsClientAsyncTestEndpoint/jaxrsclientasync?baggageValue=" + QUERY_VALUE);
        readSpans();
    }

    @Test
    public void testIntegrationWithJaxRsClientError() throws Exception {
        basicClient.get("/JaxRsClientAsyncTestEndpoint/jaxrsclienterror");
        readErrorSpans();
    }

    public void readErrorSpans() {

        spanExporter.assertSpanCount(3);

        List<SpanData> serverSpans = spanExporter.getSpansWithKind(SpanKind.SERVER);

        SpanData firstURL = null;
        SpanData secondURL = null;
        for (SpanData span : serverSpans) {
            if (span.getAttributes().get(URL_PATH).contains("JaxRsClientAsyncTestEndpoint/jaxrsclient")) {
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

        Assert.assertEquals(firstURL.getAttributes().get(HTTP_REQUEST_METHOD), HttpMethod.GET);
        Assert.assertEquals(firstURL.getAttributes().get(URL_SCHEME), "http");

        // getError returns an internal server error...
        Assert.assertEquals(secondURL.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_BAD_REQUEST);
        // Which gets received by the client
        Assert.assertEquals(httpGet.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_BAD_REQUEST);
        // The exception from the client is inspected and handled so this method should return OK
        Assert.assertEquals(firstURL.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_OK);

        // There are many different URLs that will end up here. But all should contain "JaxRsClientAsyncTestEndpoint"
        Assert.assertTrue(httpGet.getAttributes().get(URL_FULL).contains("JaxRsClientAsyncTestEndpoint"));

        Assert.assertEquals(httpGet.getAttributes().get(HTTP_REQUEST_METHOD), HttpMethod.GET);
        Assert.assertTrue(httpGet.getAttributes().get(URL_FULL).contains("JaxRsClientAsyncTestEndpoint"));
    }

    public void readSpans() {

        spanExporter.assertSpanCount(3);

        List<SpanData> serverSpans = spanExporter.getSpansWithKind(SpanKind.SERVER);

        SpanData firstURL = null;
        SpanData secondURL = null;
        for (SpanData span : serverSpans) {
            if (span.getAttributes().get(URL_PATH).contains("JaxRsClientAsyncTestEndpoint/jaxrsclient")) {
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

        Assert.assertEquals(firstURL.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_OK);
        Assert.assertEquals(firstURL.getAttributes().get(HTTP_REQUEST_METHOD), HttpMethod.GET);
        Assert.assertEquals(firstURL.getAttributes().get(URL_SCHEME), "http");
        Assert.assertTrue(firstURL.getAttributes().get(URL_QUERY).contains(QUERY_VALUE));

        // There are many different URLs that will end up here. But all should contain "JaxRsClientAsyncTestEndpoint"
        Assert.assertTrue(httpGet.getAttributes().get(URL_FULL).contains("JaxRsClientAsyncTestEndpoint"));

        Assert.assertEquals(httpGet.getAttributes().get(HTTP_RESPONSE_STATUS_CODE).intValue(), HTTP_OK);
        Assert.assertEquals(httpGet.getAttributes().get(HTTP_REQUEST_METHOD), HttpMethod.GET);
        Assert.assertTrue(httpGet.getAttributes().get(URL_FULL).contains("JaxRsClientAsyncTestEndpoint"));
    }
}
