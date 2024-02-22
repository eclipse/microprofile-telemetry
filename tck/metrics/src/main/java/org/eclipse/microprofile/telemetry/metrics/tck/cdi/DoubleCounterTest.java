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
package org.eclipse.microprofile.telemetry.metrics.tck.cdi;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import java.util.List;

import org.eclipse.microprofile.telemetry.metrics.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.metrics.tck.exporter.InMemoryMetricExporter;
import org.eclipse.microprofile.telemetry.metrics.tck.exporter.InMemoryMetricExporterProvider;
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
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import jakarta.inject.Inject;

public class DoubleCounterTest extends Arquillian {
    private static final String counterName = "testDoubleCounter";
    private static final String counterDescription = "Testing double counter";
    private static final String counterUnit = "Metric Tonnes";

    @Deployment
    public static WebArchive createTestArchive() {

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemoryMetricExporter.class, InMemoryMetricExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableMetricExporterProvider.class, InMemoryMetricExporterProvider.class)
                .addAsResource(new StringAsset(
                        "otel.sdk.disabled=false\notel.metrics.exporter=in-memory\notel.traces.exporter=none\notel.metric.export.interval=3000"),
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
    void testDoubleCounter() throws InterruptedException {
        DoubleCounter doubleCounter =
                sdkMeter
                        .counterBuilder(counterName)
                        .ofDoubles()
                        .setDescription(counterDescription)
                        .setUnit(counterUnit)
                        .build();
        Assert.assertNotNull(doubleCounter);

        doubleCounter.add(20.2, Attributes.builder().put("K", "V").build());
        doubleCounter.add(10.1, Attributes.empty());
        List<MetricData> metrics = metricExporter.getMetricData((MetricDataType.DOUBLE_SUM));
        Double value = 0.0;
        Double valueWithoutAttributes = 0.0;
        MetricData metric = metrics.get(0);

        valueWithoutAttributes = metric.getDoubleSumData().getPoints().stream()
                .filter(point -> point.getAttributes() == Attributes.empty())
                .mapToDouble(DoublePointData::getValue)
                .findFirst()
                .getAsDouble();

        value = metric.getDoubleSumData().getPoints().stream()
                .filter(point -> ("V").equals(point.getAttributes().get(stringKey("K"))))
                .mapToDouble(DoublePointData::getValue)
                .findFirst()
                .getAsDouble();

        Assert.assertEquals(value, 20.2);

        Assert.assertEquals(valueWithoutAttributes, 10.1);
    }

}
