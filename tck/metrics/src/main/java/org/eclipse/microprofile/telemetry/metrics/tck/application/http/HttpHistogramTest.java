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
package org.eclipse.microprofile.telemetry.metrics.tck.application.http;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.microprofile.telemetry.metrics.tck.application.BasicHttpClient;
import org.eclipse.microprofile.telemetry.metrics.tck.application.TestLibraries;
import org.eclipse.microprofile.telemetry.metrics.tck.application.exporter.InMemoryMetricExporter;
import org.eclipse.microprofile.telemetry.metrics.tck.application.exporter.InMemoryMetricExporterProvider;
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
    void collectsHttpRouteFromEndAttributes() {

        basicClient.get("/fail"); // Ensure we have metrics from both a successful (entering this method) and failing
                                  // HTTP call.

        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail("The test thread was interrupted");
        }

        List<MetricData> items = metricExporter.getFinishedMetricItems();

        Map<AttributeKey<String>, String> successfulHTTPMethod = new HashMap<AttributeKey<String>, String>();
        successfulHTTPMethod.put(HTTP_REQUEST_METHOD, "GET");
        successfulHTTPMethod.put(URL_SCHEME, "http");
        successfulHTTPMethod.put(HTTP_RESPONSE_STATUS_CODE, "200");
        successfulHTTPMethod.put(NETWORK_PROTOCOL_NAME, "HTTP");
        successfulHTTPMethod.put(HTTP_ROUTE, url.getPath().replaceAll("ArquillianServletRunner.*", ""));

        Map<AttributeKey<String>, String> failingHTTPMethod = new HashMap<AttributeKey<String>, String>();
        failingHTTPMethod.put(HTTP_REQUEST_METHOD, "GET");
        failingHTTPMethod.put(URL_SCHEME, "http");
        failingHTTPMethod.put(HTTP_RESPONSE_STATUS_CODE, "500");
        failingHTTPMethod.put(NETWORK_PROTOCOL_NAME, "HTTP");
        failingHTTPMethod.put(HTTP_ROUTE, url.getPath() + "fail");
        failingHTTPMethod.put(ERROR_TYPE, "500");

        testMetricItem(successfulHTTPMethod, items);
        testMetricItem(failingHTTPMethod, items);

    }

    private void testMetricItem(Map<AttributeKey<String>, String> keyAndExpectedValue, List<MetricData> items) {

        Assert.assertTrue(
                items.stream()
                        .flatMap(md -> md.getHistogramData().getPoints().stream())
                        .anyMatch(point -> {
                            return keyAndExpectedValue.entrySet().stream()
                                    .allMatch(entry -> {
                                        String attribute = point.getAttributes().get(entry.getKey());
                                        return attribute != null &&
                                                (attribute.equals(entry.getValue())
                                                        || entry.getKey().equals(HTTP_ROUTE) && entry.getValue()
                                                                .contains(keyAndExpectedValue.get(HTTP_ROUTE))
                                        // special case for Path to exclude "/ArquillianServletRunnerEE9"
                                        );
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
        public Response span() {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }

}
