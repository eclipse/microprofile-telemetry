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
package org.eclipse.microprofile.telemetry.metrics.tck.jvm;

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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import jakarta.inject.Inject;

public class JvmMemoryTest extends Arquillian {

    @Inject
    OpenTelemetry openTelemetry;

    @Deployment
    public static WebArchive createTestArchive() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemoryMetricExporter.class, InMemoryMetricExporterProvider.class)
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
    void testJvmMemoryUsedMetric() {
        MetricData metric = metricExporter.getMetricData("jvm.memory.used").get(0);
        Assert.assertEquals(metric.getDescription(), "Measure of memory used.");
        Assert.assertEquals(metric.getType(), MetricDataType.LONG_SUM);
        Assert.assertEquals(metric.getUnit(), "{By}");
    }

    @Test
    void testJvmMemoryCommittedMetric() {
        MetricData metric = metricExporter.getMetricData("jvm.memory.committed").get(0);
        Assert.assertEquals(metric.getDescription(), "Measure of memory committed.");
        Assert.assertEquals(metric.getType(), MetricDataType.LONG_SUM);
        Assert.assertEquals(metric.getUnit(), "{By}");
    }

    @Test
    void testMemoryLimitMetric() {
        MetricData metric = metricExporter.getMetricData("jvm.memory.limit").get(0);
        Assert.assertEquals(metric.getDescription(), "Measure of max obtainable memory.");
        Assert.assertEquals(metric.getType(), MetricDataType.LONG_SUM);
        Assert.assertEquals(metric.getUnit(), "{class}");
    }

    @Test
    void testMemoryUsedAfterLastGcMetric() {
        MetricData metric = metricExporter.getMetricData("jvm.memory.used_after_last_gc").get(0);
        Assert.assertEquals(metric.getDescription(),
                "Measure of memory used, as measured after the most recent garbage collection event on this pool.");
        Assert.assertEquals(metric.getType(), MetricDataType.LONG_SUM);
        Assert.assertEquals(metric.getUnit(), "{By}");
    }

}
