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
package org.eclipse.microprofile.telemetry.metrics.tck.application.cdi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.telemetry.metrics.tck.application.TestLibraries;
import org.eclipse.microprofile.telemetry.metrics.tck.application.TestUtils;
import org.eclipse.microprofile.telemetry.metrics.tck.application.exporter.InMemoryMetricExporter;
import org.eclipse.microprofile.telemetry.metrics.tck.application.exporter.InMemoryMetricExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import jakarta.inject.Inject;

public class DoubleUpDownCounterTest extends Arquillian {

    private static final String counterName = "testDoubleUpDownCounter";
    private static final String counterDescription = "Testing double up down counter";
    private static final String counterUnit = "Metric Tonnes";

    private static final double DOUBLE_WITH_ATTRIBUTES = -20;
    private static final double DOUBLE_WITHOUT_ATTRIBUTES = -10;

    @Deployment
    public static WebArchive createTestArchive() {

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemoryMetricExporter.class, InMemoryMetricExporterProvider.class, TestUtils.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableMetricExporterProvider.class, InMemoryMetricExporterProvider.class)
                .addAsResource(new StringAsset(
                        "otel.sdk.disabled=false\notel.metrics.exporter=in-memory\notel.logs.exporter=none\notel.traces.exporter=none\notel.metric.export.interval=3000"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private Meter sdkMeter;

    @Inject
    private InMemoryMetricExporter metricExporter;

    @BeforeMethod
    void setUp() {
        if (metricExporter != null) {
            metricExporter.reset();
        }
    }

    @Test
    void testDoubleUpDownCounter() throws InterruptedException {
        DoubleUpDownCounter doubleUpDownCounter =
                sdkMeter
                        .upDownCounterBuilder(counterName)
                        .ofDoubles()
                        .setDescription(counterDescription)
                        .setUnit(counterUnit)
                        .build();
        Assert.assertNotNull(doubleUpDownCounter);

        Map<Double, Attributes> expectedResults = new HashMap<Double, Attributes>();
        expectedResults.put(DOUBLE_WITH_ATTRIBUTES, Attributes.builder().put("K", "V").build());
        expectedResults.put(DOUBLE_WITHOUT_ATTRIBUTES, Attributes.empty());

        expectedResults.keySet().stream().forEach(key -> doubleUpDownCounter.add(key, expectedResults.get(key)));

        List<MetricData> metrics = metricExporter.getMetricData((counterName));
        metrics.stream()
                .peek(metricData -> {
                    Assert.assertEquals(metricData.getType(), MetricDataType.DOUBLE_SUM);
                    Assert.assertEquals(metricData.getDescription(), counterDescription);
                    Assert.assertEquals(metricData.getUnit(), counterUnit);
                })
                .flatMap(metricData -> metricData.getDoubleSumData().getPoints().stream())
                .forEach(point -> {
                    Assert.assertTrue(expectedResults.containsKey(point.getValue()),
                            "Double" + point.getValue() + " was not an expected result");
                    Assert.assertTrue(point.getAttributes().equals(expectedResults.get(point.getValue())),
                            "Attributes were not equal."
                                    + System.lineSeparator() + "Actual values: "
                                    + TestUtils.mapToString(point.getAttributes().asMap())
                                    + System.lineSeparator() + "Expected values: "
                                    + TestUtils.mapToString(expectedResults.get(point.getValue()).asMap()));
                });
    }
}
