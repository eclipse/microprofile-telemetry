/*
 **********************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICES file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 **********************************************************************/
package org.eclipse.microprofile.telemetry.metrics.tck.http;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.telemetry.metrics.tck.BasicHttpClient;
import org.eclipse.microprofile.telemetry.metrics.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.metrics.tck.exporter.InMemoryMetricExporter;
import org.eclipse.microprofile.telemetry.metrics.tck.exporter.InMemoryMetricExporterProvider;
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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

public class HttpHistogramTest extends Arquillian {

    private static final String HTTP_SERVER_REQUEST_DURATION = "http.server.request.duration";
    private static final String HTTP_SERVER_REQUEST_DURATION_DESCRIPTION = "Duration of HTTP server requests.";

    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> URL_SCHEME = AttributeKey.stringKey("url.scheme");
    private static final AttributeKey<String> HTTP_RESPONSE_STATUS_CODE =
            AttributeKey.stringKey("http.response.status_code");
    private static final AttributeKey<String> NETWORK_PROTOCOL_NAME =
            AttributeKey.stringKey("network.protocol.name");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    @Inject
    OpenTelemetry openTelemetry;

    @Deployment
    public static WebArchive createTestArchive() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemoryMetricExporter.class, InMemoryMetricExporterProvider.class,
                        BasicHttpClient.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableMetricExporterProvider.class, InMemoryMetricExporterProvider.class)
                .addAsResource(new StringAsset(
                        "otel.sdk.disabled=false\notel.metrics.exporter=in-memory\notel.logs.exporter=none\notel.traces.exporter=none\notel.metric.export.interval=3000"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @ArquillianResource
    private URL url;
    @Inject
    private InMemoryMetricExporter metricExporter;

    private BasicHttpClient basicClient;

    @BeforeMethod
    void setUp() {
        if (metricExporter != null) {
            metricExporter.reset();
            basicClient = new BasicHttpClient(url);
        }
    }

    @Test
    void testHTTPMetricAttributes() {

        // Ensure we have metrics from both a successful and failing HTTP call.
        // Using this, not the entry to this method, gives us a stable URL to test HTTP_ROUTE against
        basicClient.get("/pass");
        basicClient.get("/fail");

        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail("The test thread was interrupted");
        }

        List<MetricData> items = metricExporter.getFinishedMetricItems();
        List<MetricData> unfilteredItems = new ArrayList<MetricData>(items);

        Assert.assertFalse(items.isEmpty(), "We did not find any MetricData");

        // Filter out any MetricData objects that have the wrong name or description
        items.removeIf(md -> !md.getName().equals(HTTP_SERVER_REQUEST_DURATION));
        Assert.assertFalse(items.isEmpty(),
                "We did not find any MetricData with the name " + HTTP_SERVER_REQUEST_DURATION + " we found "
                        + unfilteredItems.stream()
                                .map(md -> md.toString())
                                .collect(Collectors.joining(", ")));

        items.removeIf(md -> !md.getDescription().equals(HTTP_SERVER_REQUEST_DURATION_DESCRIPTION));
        Assert.assertFalse(items.isEmpty(),
                "We did not find any MetricData with the descriptoin " + HTTP_SERVER_REQUEST_DURATION_DESCRIPTION
                        + " we found " + unfilteredItems.stream()
                                .map(md -> md.toString())
                                .collect(Collectors.joining(", ")));

        // Build maps of the expected attribute keys and their values
        Map<AttributeKey<String>, String> successfulHTTPMethod = new HashMap<AttributeKey<String>, String>();
        successfulHTTPMethod.put(HTTP_REQUEST_METHOD, "GET");
        successfulHTTPMethod.put(URL_SCHEME, "http");
        successfulHTTPMethod.put(HTTP_RESPONSE_STATUS_CODE, "200");
        successfulHTTPMethod.put(NETWORK_PROTOCOL_NAME, "HTTP");
        successfulHTTPMethod.put(HTTP_ROUTE, url.getPath() + "pass");

        Map<AttributeKey<String>, String> failingHTTPMethod = new HashMap<AttributeKey<String>, String>();
        failingHTTPMethod.put(HTTP_REQUEST_METHOD, "GET");
        failingHTTPMethod.put(URL_SCHEME, "http");
        failingHTTPMethod.put(HTTP_RESPONSE_STATUS_CODE, "500");
        failingHTTPMethod.put(NETWORK_PROTOCOL_NAME, "HTTP");
        failingHTTPMethod.put(HTTP_ROUTE, url.getPath() + "fail");
        failingHTTPMethod.put(ERROR_TYPE, "500");

        // Test that one of the MetricData objects with the right name and description also
        // has all the items in a map
        testMetricData(successfulHTTPMethod, items);
        testMetricData(failingHTTPMethod, items);
    }

    private void testMetricData(Map<AttributeKey<String>, String> keyAndExpectedValue, List<MetricData> items) {

        Assert.assertTrue(
                items.stream()
                        .flatMap(md -> md.getHistogramData().getPoints().stream())
                        .anyMatch(point -> {
                            return keyAndExpectedValue.entrySet().stream()
                                    .allMatch(entry -> {
                                        String attribute = point.getAttributes().get(entry.getKey());
                                        return attribute != null && attribute.equals(entry.getValue());
                                    });
                        }),
                "failed to find a metric with all items in this attribute map: " + dumpTestedMap(keyAndExpectedValue)
                        + "\n Dumping all attributes: "
                        + dumpMetricItems(items));
    }

    private String dumpTestedMap(Map<AttributeKey<String>, String> keyAndExpectedValue) {
        return keyAndExpectedValue.entrySet().stream()
                .map(entry -> entry.getKey().toString() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private String dumpMetricItems(List<MetricData> items) {

        return items.stream()
                .flatMap(md -> md.getHistogramData().getPoints().stream())
                .map(point -> point.getAttributes().toString())
                .collect(Collectors.joining(", "));
    }

    @Path("/")
    public static class SpanResource {
        @GET
        @Path("/fail")
        public Response fail() {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        @GET
        @Path("/pass")
        public Response pass() {
            return Response.ok().build();
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }

}
